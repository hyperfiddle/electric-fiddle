(ns electric-tutorial.demo-chat
  (:require
   [hyperfiddle.electric3 :as e :refer [$]]
   [hyperfiddle.electric-dom3 :as dom]))

;; TODO spine and take 10? How would we pad?
#?(:clj (def !msgs (atom (list))))

;; TODO Implement pending state
(e/defn Chat []
  (dom/ul
    (e/cursor [msg (e/server (e/diff-by identity (into '() (comp cat (take 10)) [(e/watch !msgs) (repeat nil)])))]
      (dom/li
        (dom/props {:style {:visibility (if (some? msg) "visible" "hidden")}})
        (dom/text msg))))
  (dom/input
    (dom/props {:placeholder "Type a message" :maxlength 100})
    (e/cursor [[msg spend!] ($ dom/OnAll "keydown"
                              #(when (= "Enter" (.-key %))
                                 (when-some [v (not-empty (.slice (-> % .-target .-value) 0 100))]
                                   (set! (.-value dom/node) "")
                                   v)))]
      (spend! (e/server (swap! !msgs #(cons msg (take 9 %))))))))
