(ns edos.core-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [edos.core :as e :refer [defevent defeffect]]))

(e/enable-instrumentation! true)

(deftest valid--reg-handler--ok
  (is (= ::event-tag (e/reg-handler :event-handlers ::event-tag 'event-fn)))
  (is (= :effect-tag (e/reg-handler :effect-handlers :effect-tag 'effect-fn)))
  (is (= ::effect-tag (e/reg-handler :effect-handlers ::effect-tag 'effect-fn))))

(defevent ::event-sums
  "an optional docstring"
  [a int?]
  {:effect-prints [(+ a 15)]})

(deftest valid--dispatch--ok
  (is (nil? (e/dispatch [::event-sums 1]))))

(deftest invalid-tag--dispatch--throws
  (is (thrown-with-msg? Exception #"fails spec: :edos.core/event-tag" (e/dispatch nil)))
  (is (thrown-with-msg? Exception #"fails spec: :edos.core/event-tag" (e/dispatch [nil])))
  (is (thrown-with-msg? Exception #"fails spec: :edos.core/event-tag" (e/dispatch [:unqualified-tag])))
  (is (thrown-with-msg? Exception #"nil fails at: \[:ret\] predicate: ifn\?" (e/dispatch [::unregistered-tag]))))

(defeffect :effect-prints
  "an optional docstring"
  [a int?]
  (println a))

(defevent ::bad-event-return-not-a-map
  []
  1)

(defevent ::event-returns-immediately
  [data any?]
  {:return [data]})

(defevent ::event-returns-immediately-string-arg
  [data string?]
  {:return [data]})

(defevent ::event-causes-effect
  []
  {:effect-dispatches-event []})

(defeffect :effect-dispatches-event
  []
  (e/dispatch [::event-returns-immediately :ok]))

(defevent ::event-causes-multiple-effects
  []
  {:effect-dispatches-event []
   :effect-prints           [42]})

(defevent ::event-dispatches-event
  []
  {:dispatch [::event-returns-immediately :foo]})

(defevent ::event-dispatches-multiple-events
  []
  {:dispatch-n [[::event-returns-immediately :bar] [::event-sums 25]]})

(deftest valid-immediate-return-event-dispatch--await!--ok
  (is (= {:return [nil]} (do (e/dispatch [::event-returns-immediately nil])
                             (e/await! #{:return}))))
  (is (= {:return [1]} (do (e/dispatch [::event-returns-immediately 1])
                           (e/await! #{:return}))))
  (is (= {:return ["x"]} (do (e/dispatch [::event-returns-immediately-string-arg "x"])
                             (e/await! #{:return})))))

(deftest valid-event-effect-event-dispatch--await!--ok
  (is (= {:return [:ok]} (do (e/dispatch [::event-causes-effect])
                             (e/await! #{:return}))))
  (is (= {:return [:ok]} (do (e/dispatch [::event-causes-multiple-effects])
                             (e/await! #{:return})))))

(deftest valid-event-event-dispatch--await!--ok
  (is (= {:return [:foo]} (do (e/dispatch [::event-dispatches-event])
                              (e/await! #{:return}))))
  (is (= {:return [:bar]} (do (e/dispatch [::event-dispatches-multiple-events])
                              (e/await! #{:return})))))

(deftest invalid-dispatch--await!--fail
  (is (nil? (do (e/dispatch [::event-returns-immediately])
                (e/await! #{:return}))))
  (is (nil? (do (e/dispatch [::event-returns-immediately-string-arg nil])
                (e/await! #{:return}))))
  (is (nil? (do (e/dispatch [::event-returns-immediately-string-arg 1])
                (e/await! #{:return}))))
  (is (nil? (do (e/dispatch [::bad-event-return-not-a-map])
                (e/await! #{:return})))))