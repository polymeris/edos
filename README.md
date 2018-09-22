# edos

**EXPERIMENTAL** Clojure/Script event/effect framework leveraging the power of core.async & clojure.spec.
Strongly inspired by re-frame. 

## Usage

FIXME

## Examples

### Responding to a HTTP request

```clojure
(ns example-http
  (:require [clojure.spec.alpha :as s]
            [edos.core :as e :refer :all]))

(enable-instrumentation! true)

(s/def ::path string?)
(s/def ::method #{:GET :POST})
(s/def ::request (s/keys :req-un [::method ::path]))
(s/def ::db-query string?)
(s/def ::username string?)
(s/def ::msg string?)
(s/def ::db-response-success (s/keys :req-un [::username]))
(s/def ::db-response-failure (s/keys :opt-un [::msg]))

(defeffect
  :log                           ;; this is an effect tag
  [data any?]                    ;; event and effect arguments are spec'd
  (println "effect - log" data)) ;; the effects body, the return value will be ignored

(defevent
  ::http-router                  ;; event tags must be qualified keywords
  [{:keys [method path]} ::request]
  (println "event - http request")
  ;; do routing here!
  (let [users-match (re-matches #"/users/(\d+)" path)
        user-id (last users-match)]
    ;; events are free of side effects, but they return a map which will
    ;; trigger one or more effects (or dispatch other events):
    {::db-get [(str "select * from users where id = '" user-id "'")
               ::db-get-user-success
               ::db-get-user-failure]
     :log     ["another effect"]}))

(defeffect
  ::db-get
  [query ::db-query, on-success ::e/event-tag, on-failure ::e/event-tag]
  (println "effect - side-effecty db query `" query "`")
  ;; effects do not return any values, they just cause side effects, like I/O, or dispatching events:
  (if (rand-nth [true false])
    (dispatch [on-success {:username "cecil"}])
    (dispatch [on-failure {::msg "something went wrong"}])))

(defevent
  ::db-get-user-success
  "docstrings work as expected"
  [data ::db-response-success]
  (println "event - get user successful!")
  {:response [200 data]})

(defevent
  ::db-get-user-failure
  [data ::db-response-failure]
  (println "event - get user failed! :(")
  {:response [500 (:msg data)]})

;; put it all together by dispatching an event and waiting for a certain effect (:response, in this case): 

(dispatch [::http-router {:method :GET :path "/users/42"}])

(->> (await! #{:response})
     (println "end -"))
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
