(ns electric-tutorial.input-zoo
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [electric-fiddle.fiddle :refer [#?(:cljs await-element)]]))

(e/defn PendingMonitor [edits] ; todo DirtyMonitor
  (when (pos? (e/Count edits)) (dom/props {:aria-busy true}))
  edits)

(e/defn Input* [& {:keys [maxlength type] :as props
                   :or {maxlength 100 type "text"}}]
  (e/client ; explicit site on all controls for compat with neutral callers
    (dom/input (dom/props (assoc props :maxLength maxlength :type type))
      (dom/On "input" #(-> % .-target .-value) "")))) ; no token

(e/defn Checkbox* [& {:keys [id label] :as props
                      :or {id (random-uuid)}}]
  (e/client
    (e/amb
      (dom/input (dom/props {:type "checkbox", :id id}) (dom/props (dissoc props :id :label))
        (dom/On "change" #(-> % .-target .-checked) false))
      (e/When label (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn DemoInput* []
  (let [form (dom/div
               {:str1 (Input*) ; no token
                :num1 (-> (Input* :type "number") parse-long)
                :bool1 (Checkbox*)})]
    (dom/code (dom/text (pr-str form)))))

(e/defn Input [v & {:keys [maxlength type] :as props
                    :or {maxlength 100 type "text"}}]
  (e/client
    (e/with-cycle [v (str v)] ; emits signal of current state
      (dom/input (dom/props (assoc props :maxLength maxlength :type type))
        (when-not (dom/Focused?) (set! (.-value dom/node) v))
        (dom/On "input" #(-> % .-target .-value) v))))) ; emit on boot, rebuild on reset

(e/defn Checkbox [checked & {:keys [id label] :as props
                             :or {id (random-uuid)}}]
  (e/client
    (e/amb
      (e/with-cycle [checked checked]
        (dom/input (dom/props {:type "checkbox", :id id}) (dom/props (dissoc props :id :label))
          (when-not (dom/Focused?) (set! (.-checked dom/node) checked))
          (dom/On "change" #(-> % .-target .-checked) checked)))
      (e/When label (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn DemoInput []
  (let [m (e/with-cycle [m {:str1 "hello" :num1 42 :bool1 true}] ; no atom
            (e/for [_ (e/amb 1 2)]
              (dom/div
                {:str1 (Input (:str1 m)) ; no token
                 :num1 (-> (Input (:num1 m) :type "number") parse-long)
                 :bool1 (Checkbox (:bool1 m))})))]
    (dom/code (dom/text (pr-str m))))

  #_; equivalent - cycle by atom
  (let [!m (atom {:str1 "hello" :num1 42 :bool1 true}) m (e/watch !m)]
    (dom/div
      (->> (Input (:str1 m)) (swap! !m assoc :str1))
      (->> (Input (:num1 m) :type "number") (swap! !m assoc :num1))
      (->> (Checkbox (:bool1 m)) (swap! !m assoc :bool1)))))

(e/defn Input! [v & {:keys [maxlength type] :as props
                     :or {maxlength 100 type "text"}}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength :type type))
      (PendingMonitor
        (letfn [(read [e] (let [k (.-key e)]
                            (cond
                              (= "Escape" k)  [nil (set! (.-value dom/node) v)] ; clear token
                              () [e (-> e .-target .-value (subs 0 maxlength))])))]
          ; reuse token as value updates - i.e., singular edit not concurrent
          (let [[e v'] (dom/On "input" read nil) t (e/Token e)]
            (when-not (or (dom/Focused?) (some? t)) (set! (.-value dom/node) v))
            (if t [t v'] (e/amb))))))))

(e/defn Checkbox! [checked & {:keys [id label] :as props
                              :or {id (random-uuid)}}]
  (e/client
    (e/amb
      (dom/div ; for yellow background
        (dom/props {:style {:display "inline-block" :width "fit-content"}})
        (PendingMonitor ; checkboxes don't have background so style wrapper div
          (dom/input (dom/props {:type "checkbox", :id id}) (dom/props (dissoc props :id :label))
            (let [e (dom/On "change" identity) t (e/Token e)] ; single txn, no concurrency
              (when-not (or (dom/Focused?) (some? t)) (set! (.-checked dom/node) checked))
              (if t [t ((fn [] (-> e .-target .-checked)))] (e/amb))))))
      (e/When label (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn DemoInput! [] ; async, transactional, entity backed, never backpressure
  (let [!v (e/server (atom "")) v (e/server (e/watch !v)) ; remote state
        edits (e/amb ; in-flight edits
                (Input! v)
                (Input! v))]
    (dom/code (dom/text (pr-str v)))
    (e/for [[t v] edits] ; concurrent edits
      (case (e/server ; remote transaction
              (e/Offload #(do (reset! !v v) ::ok)))
        ::ok (t)))) ; clear edit on success
  (dom/br)
  (let [!v (e/server (atom false)) v (e/server (e/watch !v))
        edits (e/amb
                (Checkbox! v)
                (Checkbox! v))]
    (dom/code (dom/text (pr-str v)))
    (e/for [[t v] edits]
      (case (e/server
              (e/Offload #(do (reset! !v v) ::ok)))
        ::ok (t)))))

(e/defn InputSubmit! [v & {:keys [maxlength type] :as props
                           :or {maxlength 100 type "text"}}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength :type type))
      (PendingMonitor
        (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
                (submit! [e] (let [k (.-key e)]
                               (cond
                                 (= "Enter" k) (read! (.-target e)) ; no clear
                                 (= "Escape" k) (do (set! (.-value dom/node) "") nil)
                                 () nil)))]
          (dom/OnAll "keydown" submit!))))))

(e/defn CheckboxSubmit! [checked & {:keys [id label] :as props
                                    :or {id (random-uuid)}}]
  (e/client
    (e/amb
      (dom/input (dom/props {:type "checkbox", :id id}) (dom/props (dissoc props :id :label))
        (let [edits (dom/OnAll "change" #(-> % .-target .-checked))] ; concurrent tx processing
          (when-not (or (dom/Focused?) (pos? (e/Count edits)))
            (set! (.-checked dom/node) checked))
          edits))
      (e/When label (dom/label (dom/props {:for id}) (dom/text label))))))

(e/defn DemoInputSubmit! []
  (let [!v (e/server (atom "")) v (e/server (e/watch !v)) ; remote state
        edits (e/amb ; in-flight edits
                (InputSubmit! v)
                (InputSubmit! v))]
    (dom/code (dom/text (pr-str v)))
    (e/for [[t v] edits] ; concurrent edits
      (case (e/server ; remote transaction
              (e/Offload #(do (reset! !v v) ::ok)))
        ::ok (t)))))

(e/defn InputSubmitClear! [& {:keys [maxlength type] :as props
                              :or {maxlength 100 type "text"}}]
  (e/client
    (dom/input (dom/props (assoc props :maxLength maxlength :type type))
      (letfn [(read! [node] (not-empty (subs (.-value node) 0 maxlength)))
              (read-clear! [node] (when-some [v (read! node)] (set! (.-value node) "") v))
              (submit! [e] (let [k (.-key e)]
                             (cond
                               (= "Enter" k) (read-clear! (.-target e))
                               (= "Escape" k) (do (set! (.-value dom/node) "") nil)
                               () nil)))]
        (PendingMonitor
          (dom/OnAll "keydown" submit!))))))

(e/defn DemoInputSubmitClear! []  ; chat
  (let [!v (e/server (atom "")) v (e/server (e/watch !v)) ; remote state
        edits (e/amb ; in-flight edits
                (InputSubmitClear! :placeholder "Send")
                (InputSubmitClear! :placeholder "Send"))]
    (dom/code (dom/text (pr-str v)))
    (e/for [[t v] edits] ; concurrent edits
      (case (e/server ; remote transaction
              (e/Offload #(do (reset! !v v) ::ok)))
        ::ok (t))))) ; clear edit on success

(declare css)

(e/defn InputZoo []
  (dom/style (dom/text css))
  (dom/p (dom/text "See portals!"))
  (e/for [[Demo selector] (e/amb
                            [DemoInput* "#zoo_input_star_demo"]
                            [DemoInput "#zoo_input_demo"]
                            [DemoInput! "#zoo_input_bang_demo"]
                            [DemoInputSubmit! "#zoo_input_submit_bang_demo"]
                            [DemoInputSubmitClear! "#zoo_input_submit_clear_bang_demo"])]
    (binding [dom/node (e/Task (await-element js/document.body selector))]
      (dom/div ; workaround reverse rendering bug
        (Demo)))))

(def css "
dl.InputZoo { margin: 0; display: grid;  grid-template-columns: 1fr 1fr; row-gap: 1em; }
dl.InputZoo dt { grid-column: 1; font-weight: 700; }
dl.InputZoo dd { grid-column: 2; margin-bottom: .5rem; margin-left: 1em; }
dl.InputZoo p { font-weight: 500; font-size: .9em; }
dl.InputZoo input[type=text] { width: 10ch; }
dl.InputZoo p { margin: 0 0 .5em; }

.user-examples-readme table { max-width:100vw; }
.user-examples-readme table { display: grid; grid-template-columns: 40% 60%; }
.user-examples-readme table tbody {display:contents;}
.user-examples-readme table tr {display:contents;}
.user-examples-readme table .user-examples { font-size: 13px; }
.user-examples-readme table p { margin-top: 1em; }
.user-examples-readme [aria-busy='true'] { background-color: yellow; }
.user-examples-readme input[type=text],
.user-examples-readme input[type=number] { width: 10ch; }


") ; todo check mobile and responsive