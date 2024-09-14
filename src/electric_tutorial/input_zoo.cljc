(ns electric-tutorial.input-zoo
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]))

(e/defn PendingMonitor [edits]
  (when (pos? (e/Count edits)) (dom/props {:aria-busy true}))
  edits)

(e/defn Input* [& {:keys [maxlength] :or {maxlength 100} :as props}]
  (e/client ; explicit site on all controls for compat with neutral callers
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (dom/On "input" #(-> % .-target .-value) "")))) ; no token

(e/defn DemoInput* [] ; webview
  (let [!v (atom "") v (e/watch !v)] ; simple state, synchronous updates
    (->> (Input* :placeholder "Search") (reset! !v)) ; no token
    (->> (Input* :placeholder "Search") (reset! !v))
    (dom/pre (dom/text (pr-str v)) (dom/props {:style {:margin 0}}))))

(e/defn Input [v & {:keys [maxlength] :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (when-not (dom/Focused?)
        (set! (.-value dom/node) (str v)))
      (dom/On "input" #(-> % .-target .-value) (str v))))) ; emits initial state on boot

(e/defn DemoInput [] ; temperature converter
  (let [!v (atom "") v (e/watch !v)] ; simple state, synchronous updates
    (->> (Input v :placeholder "Search") (reset! !v)) ; no token
    (->> (Input v :placeholder "Search") (reset! !v))
    (dom/pre (dom/text (pr-str v)) (dom/props {:style {:margin 0}}))))

(e/defn Input! [v & {:keys [maxlength] :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (PendingMonitor
        (letfn [(read! [node] (subs (.-value node) 0 maxlength))
                (read [e] (let [k (.-key e)]
                            (cond
                              (= "Escape" k) (do (set! (.-value dom/node) v) nil) ; clear token with nil
                              () (read! (.-target e)))))]
          ; reuse token as value updates - i.e., singular edit not concurrent
          (let [v' (dom/On "input" read nil) t (e/Token v')]
            (when-not (or (dom/Focused?) (some? t)) (set! (.-value dom/node) v))
            (if t [t v'] (e/amb))))))))

(e/defn DemoInput! [] ; async, transactional, entity backed, never backpressure
  (let [!v (e/server (atom "")) v (e/server (e/watch !v)) ; remote state
        edits (e/amb ; in-flight edits
                (Input! v :placeholder "Edit cell")
                (Input! v :placeholder "Edit cell"))]
    (dom/pre (dom/text (pr-str v)) (dom/props {:style {:margin 0}}))
    (e/for [[t v] edits] ; concurrent edits
      (case (e/server ; remote transaction
              (e/Offload #(do (reset! !v v) ::ok)))
        ::ok (t))))) ; clear edit on success

(e/defn InputSubmit! [& {:keys [maxlength] :or {maxlength 100} :as props}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength))
      (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
              (read-clear! [node] (when-some [v (read! node)] (set! (.-value node) "") v))
              (submit! [e] (let [k (.-key e)]
                             (cond
                               (= "Enter" k) (read-clear! (.-target e))
                               (= "Escape" k) (do (set! (.-value dom/node) "") nil)
                               () nil)))]
        (dom/OnAll "keydown" submit!)))))

(e/defn DemoInputSubmit! []  ; chat
  (let [!v (e/server (atom "")) v (e/server (e/watch !v)) ; remote state
        edits (e/amb ; in-flight edits
                (InputSubmit! :placeholder "Send message")
                (InputSubmit! :placeholder "Send message"))]
    (dom/pre (dom/text (pr-str v)) (dom/props {:style {:margin 0}}))
    (e/for [[t v] edits] ; concurrent edits
      (case (e/server ; remote transaction
              (e/Offload #(do (reset! !v v) ::ok)))
        ::ok (t))))) ; clear edit on success

(declare css)

(e/defn InputZoo []
  (dom/dl (dom/style (dom/text css))
    (e/amb ; workaround crash, fixme
      (dom/dt (dom/text "Input*")) (dom/dd (DemoInput*))
      (dom/dt (dom/text "Input")) (dom/dd (DemoInput))
      (dom/dt (dom/text "Input!")) (dom/dd (DemoInput!))
      (dom/dt (dom/text "InputSubmit!")) (dom/dd (DemoInputSubmit!)))))

(def css ".user-examples-target.InputZoo dt { margin-bottom: 3em; }")