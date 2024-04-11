(ns datagrid.gitbrowser
  (:require #?(:clj [clj-jgit.porcelain :refer [load-repo]])
            [contrib.color]
            [clojure.datafy :refer [datafy]]
            [datagrid.datafy-git #?(:clj :as, :cljs :as-alias) git]
            [datagrid.datafy-renderer :as r]
            [datagrid.file-explorer :refer [RouterInput]]
            [datagrid.schema :as schema]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-css :as css]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.router :as router]
            [heroicons.electric.v24.outline :as icons]
            [clojure.string :as str]
            #?(:cljs [goog.date.relative :refer [formatPast]])))

(e/def repo-path ".")
(e/def branches {})

(e/defn ClosePanelButton []
  (e/client
    (dom/button
      (dom/props {:title "Close panel"
                  :class (css/scoped-style
                           (css/rule {:width                      "1.25rem"
                                      :height                     "1.25rem"
                                      :position                   :absolute
                                      :top                        0
                                      :left                       0
                                      :right                      0
                                      :margin-left                :auto
                                      :margin-right               :auto
                                      :background-color           :white
                                      :box-sizing                 :content-box
                                      :padding                    "0.125rem 0.5rem"
                                      :box-shadow                 "0 0.25rem .75rem lightgray"
                                      :border                     "1px white solid"
                                      :border-bottom-right-radius "50%"
                                      :border-bottom-left-radius  "50%"
                                      :z-index                    2})
                           (css/rule ":hover" {:cursor :pointer
                                               :transform "scale(1.05)"}))})
      (icons/chevron-down)
      (dom/on "click" (e/fn* []
                        (router/Navigate!. ['.. `(GitBrowser ~repo-path)]))))))

#?(:cljs
   (defn format-time [inst]
     (let [fmt (js/Intl.DateTimeFormat. js/undefined #js {:timeStyle "short"})]
       (.format fmt inst))))

#?(:cljs
   (defn format-date [inst]
     (let [fmt (js/Intl.DateTimeFormat. js/undefined #js {:dateStyle "short"})]
       (.format fmt inst))))

#?(:cljs
   (defn format-relative [inst]
     (let [now (js/Date.)]
       (not-empty
         (let [today?     (< (- now inst) (* 1000 60 60 24))
               yesterday? (< (- now inst) (* 1000 60 60 24 2))]
           (cond
             today?     (formatPast (.getTime inst))
             yesterday? (str (formatPast (.getTime inst)) " " (format-time inst))))))))

#?(:cljs
   (defn format-commit-time
     ([inst] (format-commit-time true inst))
     ([relative? inst]
      (or (and relative? (format-relative inst))
        (str (format-date inst) " " (format-time inst))))))

(e/defn RenderCommitTime [props e a V]
  (e/server
    (let [inst (V.)]
      (e/client
        (dom/props {:title (format-commit-time false inst)})
        (dom/text (format-commit-time inst))))))

(e/defn CommitMetadata [commit]
  (e/client
    (dom/div (dom/props {:style {:grid-row 1, :grid-column "1 / 3"}})
      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/schema-registry (schema/registry {:id :string, :author :string, :time inst?, :email :string})
                  r/renderers  (assoc r/renderers :time RenderCommitTime)]
          (r/RenderForm. {::r/row-height-px 25
                          ::r/max-height-px (* 25 6)
                          ::r/keys [:id :author :merge :time :email]}
            nil nil (e/fn* [] commit)))
        (let [[head & details] (str/split-lines (:message commit))]
          (e/client
            (dom/pre (dom/props {:style {:font-size "1.125rem" :margin-top 0, :margin-left "0.5rem", :margin-bottom 0}}) (dom/text head))
            (dom/pre (dom/props {:style {:margin-top 0, :margin-left "0.5rem"}}) (dom/text (str/join "\n" details)))))))))

(e/defn RenderDiffLink [props e a V]
  (e/server
    (let [v (V.)]
      (e/client
        (router/link ['. :diff v]
          (dom/text v))))))

(e/defn RenderGitStat [type props e a V]
  (e/server
    (let [v (V.)]
      (when-not (zero? v)
        (e/client
          (dom/props {:style {:color (case type ::additions :green, ::deletions :red :inherit)}})
          (dom/text (case type ::additions "+", ::deletions "-" "") v))))))

(e/defn ChangesList [changed-files]
  (e/client
    (dom/div
      (dom/props {:style {:display :flex, :flex-direction :column, :grid-row 3, :position :sticky, :top 0 :height "auto"}})
      (dom/h2  (dom/props {:style {:font-size "1.25rem", :font-weight 500, :margin "0 0 0 0.5rem"}}) (dom/text "Changes"))
      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/schema-registry (schema/registry {:path :string})
                  r/renderers       (assoc r/renderers ::git/path RenderDiffLink
                                      ::git/additions (e/partial 5 RenderGitStat ::additions)
                                      ::git/deletions (e/partial 5 RenderGitStat ::deletions))]
          (r/RenderGrid.
            {::r/header?       false
             ::r/row-height-px 25
             ::r/max-height-px "100%"
             ::r/columns       [#_{::r/attribute :type ::r/title ""}
                                {::r/attribute ::git/path, ::r/title ""}
                                {::r/attribute ::git/additions, ::r/title ""}
                                {::r/attribute ::git/deletions, ::r/title ""}]
             ::dom/props       {:style {:grid-template-columns "auto min-content min-content"
                                        :min-height            "25px"}}}
            nil nil
            (e/fn [] changed-files)))))))

(e/defn DiffView [diff]
  (e/client
    (dom/div
      (dom/props {:class (css/scoped-style
                           (css/rule {:height :fit-content, :overflow-x :hidden, :grid-column 2, :grid-row 3})
                           (css/rule ".d2h-file-header" {:display :none}))})
      (let [config (js-obj "drawFileList" false
                     "fileListToggle" false
                     "fileListStartVisible" false
                     "fileContentToggle" false
                     "matching" "words" ; "lines"
                     "outputFormat" #_"line-by-line" "side-by-side"
                     "synchronisedScroll" true
                     "stickyFileHeaders" false
                     "highlight" true
                     "renderNothingWhenEmpty" false)
            ui     ^js (js/Diff2HtmlUI. dom/node diff config)]
        (.draw ui)))))

(e/defn CommitInfo  [commit]
  (e/server
    (e/client
      (dom/div
        (dom/props {:style {:border-top "2px lightgray solid"
                            :overflow   :auto
                            :position   :relative
                            :height :auto
                            :display :grid
                            :grid-template-columns "auto 1fr"}})

        (ClosePanelButton.)
        (e/server
          (CommitMetadata. commit)
          (let [changed-files (::git/changes commit)]
            (ChangesList. changed-files)
            (let [diffs (git/diffs (:repo commit) (git/parent-commit (:raw commit)) (:raw commit) ::git/default)]
              (DiffView. (get diffs (e/client (ffirst (get router/route :diff))) (get diffs (::git/path (first changed-files))))))))))))


(e/defn RenderCommitId [props e a V]
  (e/server
    (let [commit-id (git/short-commit-id (V.))]
      (e/client
        (router/link ['. :details commit-id]
          (dom/props {:style {:font-family "monospace"}})
          (dom/text commit-id))))))

(e/defn RenderLine [props e a V]
  (e/client
    (dom/text "│")))

(defn format-branch [str] (str/replace-first str #"refs/heads/" ""))

(defn branch-color [branch-ref-name] (contrib.color/color branch-ref-name (/ 63 360) 55 65))

(e/defn RenderMessage [props e a V]
  (e/server
    (let [[_ [_e _a V-1]] r/stack
          commit          (r/JoinValue. (V-1.))
          branches        (::git/branches commit)
          message         (V.)]
      (e/client
        (dom/props {:style {:display :flex
                            :gap     "0.25rem"}})
        (when (seq branches)
          (e/for [branch (map format-branch branches)]
            (dom/span (dom/props {:style {:border           "2px white solid"
                                          :color            :white
                                          :border-radius    "7px"
                                          :padding          "0.125rem 0.5rem"
                                          :box-sizing       :border-box
                                          :font-size        "0.75rem"
                                          :font-family      "monospace, sans serif"
                                          :background-color (branch-color branch)}})
                      (dom/text branch))))
        (dom/span
          (dom/text message))))))

(e/defn GitLog [repo]
  (e/client
    (RouterInput. {::dom/type :search
                   ::dom/placeholder "Search for commits"}
      :message)
    (dom/div
      (dom/props {:style {:max-height "100%"
                          :overflow   :auto
                          :position   :relative}})
      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/schema-registry (schema/registry {:id      :string
                                                      :author  :string
                                                      :message :string
                                                      :time    inst?})
                  r/renderers       (assoc r/renderers :id RenderCommitId
                                      :time RenderCommitTime
                                      :line RenderLine
                                      :message RenderMessage)]
          (r/RenderGrid.
            {::r/row-height-px 25
             ::r/max-height-px "100%"
             ::r/columns       [{::r/attribute :id}
                                {::r/attribute :line, ::r/title ""}
                                {::r/attribute :message}
                                {::r/attribute :author}
                                {::r/attribute :time, ::r/sortable true}]
             ::dom/props       {:style {:grid-template-columns "min-content min-content auto min-content min-content"}}}
            nil nil
            (e/fn []
              (->> (r/Nav. (datafy repo) :log)
                ;; (take 1)
                (r/InputFilter. :msg :message) ; :msg vs :message is a clj-jgit inconsistency
                (r/ColumnSort. (fn [column]
                                 (case column ; Account for clj-jgit naming inconsistencies
                                   :time #(get-in % [:author :date]) ; clj-git stores time in [:author :date] for the log, but in :time for the commit itself
                                   column)))))))))))

(e/defn GitBrowser [& [git-repo-path git-commit-id]]
  (e/client
    (dom/props {:style {;; FIXME make it a rule, conflict with "examples.css"
                        :height             "100dvh"
                        :box-sizing         :border-box
                        :padding            "1rem"
                        :padding-bottom     "0.5rem"
                        :margin             0
                        :display            :grid
                        :gap                "0.75rem"
                        :grid-auto-flow     :column
                        :overflow           :hidden
                        :grid-template-rows "min-content min-content auto fit-content(50%)"
                        }
                :class (css/scoped-style
                         (css/rule {:height             "100dvh"
                                    :box-sizing         :border-box
                                    :padding            "1rem"
                                    :padding-bottom     "0.5rem"
                                    :margin             0
                                    :display            :grid
                                    :gap                "0.75rem"
                                    :grid-auto-flow     :column
                                    :overflow           :hidden
                                    :grid-template-rows "min-content min-content auto fit-content(50%)"})
                         (css/rule ".virtual-scroll" {:flex 1, :max-height "100%"})
                         (css/rule ".datagrid > tr > td, .datagrid > thead th" {:padding-left "0.5rem", :padding-right "0.5em", :border :none})
                         (css/rule ".datagrid > tr:nth-child(odd) > td" {:background-color :whitesmoke})
                         (css/rule ".d2h-file-list-wrapper" {:position :sticky, :top 0, :height :min-content}))}))
  (e/server
    (binding [repo-path (or git-repo-path ".")]
      (let [repo (load-repo repo-path)]
        (binding [branches (git/get-branches repo)]
          (GitLog. repo)
          (e/client
            (when-let [commit-id (:details router/route)]
              (e/server (CommitInfo. (datafy (git/get-commit repo (ffirst commit-id))))))))))))

