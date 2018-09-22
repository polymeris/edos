(defproject edos "0.1.0-SNAPSHOT"
  :description "*Experimental* Clojure/Clojurescript event- and effect-handling patterns"
  :url "https://github.com/polymeris/edos"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/core.async "0.4.474"]
                 [orchestra "2018.09.10-1"]]
  :plugins [[lein-codox "0.10.4"]]
  :codox {:metadata     {:doc/format :markdown}
          :exclude-vars #"^-.*-\d+"})