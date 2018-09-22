(ns edos.core
  "Provides facilities for defining, instrumenting and registering events and effects.
   See https://github.com/polymeris/edos for a description on how to use this library."
  (:require #?@(:clj  [[clojure.core.async :as a :refer [go-loop]]
                       [clojure.spec.alpha :as s]
                       [orchestra.core :refer [defn-spec]]
                       [orchestra.spec.test :as otest]]
                :cljs [[cljs.core.async :as a]
                       [cljs.spec.alpha :as s]
                       [orchestra.core :refer-macros [defn-spec]]]))
  #?(:cljs (:require-macros [cljs.core.async.macros :refer [go-loop]])))

(s/def ::spec (s/or :registered-spec s/get-spec
                    :spec-object s/spec?
                    :spec-regex s/regex?
                    :predicate (s/and ifn? (complement keyword?))))
(s/def ::event-tag qualified-keyword?)
(s/def ::effect-tag keyword?)
(s/def ::event (s/cat :tag ::event-tag :data (s/* any?)))

(def ^:private registry
  (atom {:instrumentation true
         :event-handlers  {}
         :effect-handlers {}
         :chan            (a/chan)}))

(defn- maybestrument! []
  (if (:instrumentation @registry)
    (otest/instrument)
    (otest/unstrument)))

(defn- gentagsym
  "Symbols prefixed with tag name provide a bit friendlier error outputs"
  [tag]
  (gensym (str (namespace tag) "-" (name tag) "-")))

(defn enable-instrumentation!
  "Enables or disables handler instrumentation, i.e. runtime arguments and return value checking."
  [enabled]
  (swap! registry assoc :instrumentation (boolean enabled))
  (maybestrument!))

(defn-spec reg-handler keyword?
  "Don't use directly, access through [[defevent]] and [[defeffect]] macros.
   Registers an event or effect handler function with the registry, and instruments them if enabled via
   [[enable-instrumentation]]."
  [kind #{:event-handlers :effect-handlers}
   tag keyword?
   fn-sym symbol?]
  (swap! registry assoc-in [kind tag] (resolve fn-sym))
  (maybestrument!)
  tag)

(defmacro defevent
  "Define and register an event handler for the given tag.
   Event handlers:

     * contain the majority of the domain logic
     * are pure functions, and thus can easily be unit tested
     * are handled asynchronously
     * return a map of effects to be handled
     * cannot directly dispatch other events, see `:dispatch` effect

   Example usage:
   ```
   (defevent ::an-event-tag                    ; a qualified keyword acts as event tag
     \"an optional docstring\"
     [a int?]                                  ; list of arguments with specs
     {:an-effect-tag [(+ a 15)]})              ; event handler body, must return a map of effects
   ```

   To access the underlying function, e.g. to write unit tests use [[event-handler]]."
  [tag & args]
  (let [sym (gentagsym tag)]
    `(reg-handler
       :event-handlers
       ~tag
       (defn-spec ~sym (s/map-of ::effect-tag vector?)
         ~@args))))

(s/fdef defevent
  :args (s/cat :tag ::event-tag
               :docstring? (s/? string?)
               :params (s/coll-of any?)
               :body (s/* any?))
  :ret qualified-keyword?
  :fn #(= (:ret %) (-> % :args :tag)))

(defmacro defeffect
  "Define and register an effect handler for the given tag.
   Effect handlers:

     * should be as simple as possible
     * involve side effects, often I/O, thus cannot be unit tested without mocking
     * are handled synchronously
     * return value is ignored
     * can dispatch events

   Example usage:
   ```
   (defeffect :an-effect-tag                   ; a keyword (qualified or not) acts as effect tag
     \"an optional docstring\"
     [a int?]                                  ; list of arguments with specs
     (do-something-side-effecty! a)            ; effect handler body, return value will be ignored
     (edos.core/dispatch [::an-event-tag a]))  ; effect handlers often dispatch events
   ```

   To access the underlying function, use [[effect-handler]]."
  [tag & args]
  (let [sym (gentagsym tag)]
    `(reg-handler
       :effect-handlers
       ~tag
       (defn-spec ~sym nil?
         ~@args
         nil))))

(s/fdef defeffect
  :args (s/cat :tag ::effect-tag
               :docstring? (s/? string?)
               :params (s/coll-of any?)
               :body (s/* any?))
  :ret keyword?
  :fn #(= (:ret %) (-> % :args :tag)))

(defn-spec event-handler ifn?
  "Returns the function registered as handler for the given event tag."
  [tag ::event-tag]
  (-> @registry :event-handlers tag))

(defn-spec effect-handler ifn?
  "Returns the function registered as handler for the given effect tag."
  [tag ::effect-tag]
  (-> @registry :effect-handlers tag))

(defn-spec dispatch nil?
  "Dispatch an event.
   Event will be handled asynchronously by the handler
   matching the event's tag.
   Can also be invoked through `:dispatch` and `:dispatch-n` effects:
   ```
   {:dispatch [::foo 1]
    :dispatch-n [[::bar true] [::baz false]]}
   ```"
  [event ::event]
  (assert (event-handler (first event)))
  (a/put! (:chan @registry) event)
  nil)

(defeffect :dispatch
  "The `:dispatch` effect causes an event to be dispatched.
   The event handler will be executed asynchronously."
  [tag ::event-tag & args any?]
  (dispatch (into [tag] args)))

(defn- dispatch-n
  "The `:dispatch-n` effect causes multiple events to be dispatched.
   The event handlers will be executed asynchronously.

   This is registered using [[reg-handler]], since [[defeffect]] currently does not support variadic functions."
  [& events]
  (doall (map dispatch events)))

(reg-handler :effect-handlers :dispatch-n 'dispatch-n)

(defn- do-effects!
  [event-result]
  (doall (map (fn [[tag args]] (apply (effect-handler tag) args))
              event-result)))

(defn-spec await! any?
  "Start event loop and block until a terminal effect is triggered.
   `terminal-effect-tags` should be a set of tags.
   Returns map of effects or nil if an effect or event handler throws an exception."
  [terminal-effect-tags (s/coll-of ::effect-tag)]
  (a/<!!
    (go-loop []
      (let [[tag & args] (a/<! (:chan @registry))
            event-result (apply (event-handler tag) args)
            ret (select-keys event-result terminal-effect-tags)]
        (if-not (empty? ret)
          ret
          (do (do-effects! event-result)
              (recur)))))))