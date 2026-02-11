(ns dustingetz.talks.lifecycle "http://localhost:8080/dustingetz.talks.lifecycle!Lifecycle/"
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn Blinker []
  (dom/h1 (dom/text "blink")))

(e/defn Lifecycle []
  (e/client
    (let [is-even (even? (e/System-time-secs))]
      (if is-even
        (Blinker)))))

#_(e/server (e/System-time-ms))