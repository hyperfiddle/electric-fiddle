(ns datagrid.gitbrowser
  (:require #?(:clj [clj-jgit.porcelain :refer [load-repo]])
            [clojure.datafy :refer [datafy]]
            [datagrid.datafy-git #?(:clj :as, :cljs :as-alias) git]
            [datagrid.datafy-renderer :as r]
            [datagrid.file-explorer :refer [RouterInput]]
            [datagrid.schema :as schema]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-css :as css]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.router :as router]
            [clojure.string :as str]))

(e/def repo-path ".")

(e/defn CommitInfo  [commit]
  (e/server
    (let [diffs (git/diffs (:repo commit) (git/parent-commit (:raw commit)) (:raw commit) ::git/default)]
      (e/client
        (dom/div (dom/props {:style {:overflow :auto
                                     :display :grid
                                     :grid-template-columns "auto 1fr"
                                     :position :relative
                                     ;; :padding-top "0.5rem"
                                     :border-top "2px lightgray solid"}})
          (let [str (str/join "\n\n" diffs)
                config (js-obj "drawFileList" true
                         "fileListToggle" true
                         "fileListStartVisible" true
                         "fileContentToggle" true
                         "matching" "words" ; "lines"
                         "outputFormat" "side-by-side"
                         "synchronisedScroll" true
                         "stickyFileHeaders" true
                         "highlight" true
                         "renderNothingWhenEmpty" false)
                ui ^js (js/Diff2HtmlUI. dom/node str config)]
            (.draw ui)))))))


(e/defn RenderCommitId [props e a V]
  (e/server
    (let [commit-id (git/short-commit-id (V.))]
      (e/client
        (router/link ['. :details commit-id]
          (dom/props {:style {:font-family "monospace"}})
          (dom/text commit-id))))))

(e/defn GitLog [repo]
  (e/client
    (RouterInput. {::dom/type :search} :message)
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
                  r/renderers       (assoc r/renderers :id RenderCommitId)]
          (r/RenderGrid.
            {::r/row-height-px 25
             ::r/max-height-px "100%"
             ::r/columns       [{::r/attribute :id}
                                {::r/attribute :author}
                                {::r/attribute :message}
                                {::r/attribute :time, ::r/sortable true}]
             ::dom/props       {:style {:grid-template-columns "min-content min-content auto min-content"}}}
            nil nil
            (e/fn []
              (->> (r/Nav. (datafy repo) :log)
                (r/InputFilter. :msg :message) ; :msg vs :message is a clj-jgit inconsistency
                (r/ColumnSort. (fn [column]
                                 (case column ; Account for clj-jgit naming inconsistencies
                                   :time #(get-in % [:author :date]) ; clj-git stores time in [:author :date] for the log, but in :time for the commit itself
                                   column)))))))))
    ))

(e/defn GitBrowser [& [git-repo-path git-commit-id]]
  (e/client
    (dom/props {:style {;; FIXME make it a rule, conflict with "examples.css"
                        :height             "100dvh"
                        :box-sizing         :border-box
                        :padding            "1rem"
                        :padding-bottom     "0.5rem"
                        :margin             0
                        :display            :grid
                        :gap                "1rem"
                        :grid-auto-flow     :column
                        :overflow           :hidden
                        :grid-template-rows "min-content min-content auto fit-content(50%)"
                        }
                :class (css/scoped-style
                         (css/rule ".virtual-scroll" {:flex 1, :max-height "100%"})
                         (css/rule ".datagrid > tr > td, .datagrid > thead th" {:padding-left "0.5rem", :padding-right "0.5em"})
                         (css/rule ".datagrid > tr:nth-child(odd) > td" {:background-color :whitesmoke})
                         (css/rule ".d2h-file-list-wrapper" {:position :sticky, :top 0, :height :min-content}))}))
  (e/server
    (binding [repo-path (or git-repo-path ".")]
      (let [repo (load-repo repo-path)]
        (GitLog. repo)
        (e/client
          (when-let [commit-id (:details router/route)]
            (e/server (CommitInfo. (git/get-commit repo (ffirst commit-id))))))))))


