(ns datagrid.gitbrowser
  (:require #?(:clj [clj-jgit.porcelain :refer [load-repo]])
            [clojure.datafy :refer [datafy]]
            [datagrid.datafy-git #?(:clj :as, :cljs :as-alias) git]
            [datagrid.datafy-renderer :as r]
            [datagrid.file-explorer :refer [RouterInput]]
            [datagrid.popover :as popover]
            [datagrid.schema :as schema]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-css :as css]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.router :as router]))

(e/def repo-path ".")

(e/defn CommitInfo [commit]
  (e/server
    (binding [r/Render          r/SchemaRenderer
              r/schema-registry (schema/registry {:id :string, :message :string})]
      (r/RenderForm. {::r/row-height-px 25
                      ::r/max-height-px "100%"}
        nil nil (e/fn [] (dissoc commit :repo :raw))))))

(e/defn FiddlePopover
  "A Popover rendering a fiddle, allows recursive, lazy fiddle nesting."
  [path AnchorBody]
  (popover/Popover. {::popover/path path, ::popover/AnchorBody AnchorBody}
    (e/fn []
      (let [[f & args :as route] (first (router/resolve-path path))]
        (e/apply (get hyperfiddle/pages f electric-fiddle.main/NotFoundPage) args)))))

(e/defn RenderCommitId [props e a V]
  (e/server
    (let [commit-id (git/short-commit-id (V.))]
      (e/client
        (FiddlePopover. ['.. `(GitBrowser ~repo-path ~commit-id)]
          (e/fn []
            (dom/props {:style {:font-family "monospace"}})
            (dom/text commit-id)))))))

(e/defn GitLog [repo]
  (e/client
    (dom/props {:class (css/scoped-style
                         (css/rule {:height         "100vh"
                                    :padding-bottom 0
                                    :box-sizing     :border-box
                                    :margin         0
                                    :display        :flex
                                    :flex-direction :column})
                         (css/rule ".virtual-scroll" {:flex 1})
                         (css/rule ".datagrid td, .datagrid th" {:padding-left "0.5rem", :padding-right "0.5em"})
                         (css/rule ".datagrid tr:nth-child(odd) td" {:background-color :whitesmoke}))})
    (RouterInput. {::dom/type :search} :message)
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
            (r/ColumnSort. (r/InputFilter. :msg :message (r/Nav. (datafy repo) :log)))))))))

(e/defn GitBrowser [& [git-repo-path git-commit-id]]
  (e/server
    (binding [repo-path (or git-repo-path ".")]
      (let [repo (load-repo repo-path)]
        (if git-commit-id
          (CommitInfo. (git/get-commit repo git-commit-id))
          (GitLog. repo))))))
