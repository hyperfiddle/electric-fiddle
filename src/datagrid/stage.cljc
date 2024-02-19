(ns datagrid.stage
  (:require [hyperfiddle.electric :as e]))

(e/def Commit)                          ; e/fn
(e/def discard!)                        ; fn
(e/def stage)                           ; current stage value
(e/def stage! (fn [_]))                 ; set current stage

(e/defn* Stage [OnCommit Body]
  (let [!stage (atom nil)]
    (binding [stage    (e/watch !stage)
              discard! (fn [] (reset! !stage nil))
              stage!   (partial reset! !stage)
              Commit   (e/fn* [] (OnCommit. @!stage))]
      (Body.)))
  nil)

(defmacro staged [OnCommit & body]
  `(new Stage ~OnCommit (e/fn* [] ~@body)))
