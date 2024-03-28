(ns dustingetz.gitbrowser
  (:import [hyperfiddle.electric Pending])
  (:require #?(:clj [clj-jgit.porcelain :refer [load-repo]])
            [clojure.datafy :refer [datafy]]
            [datagrid.datafy-renderer :as r]
            [datagrid.file-explorer :refer [RouterInput]]
            [datagrid.schema :as schema]
            #?(:clj [dustingetz.datafy-git :as git])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-css :as css]
            [hyperfiddle.electric-dom2 :as dom]))

(e/defn GitLog [repo]
  (e/client
    (dom/props {:style {:height         "100vh"
                        :padding-bottom 0
                        :box-sizing     :border-box
                        :margin         0
                        :display        :flex
                        :flex-direction :column}
                :class [(css/scoped-style
                          (css/rule ".virtual-scroll" {:flex 1}))]})
    (try
      (RouterInput. {} :msg)
      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/schema-registry (schema/registry
                                      {:log [:sequential {:cardinality :many} :any]
                                       :msg :string
                                       :dustingetz.datafy-git/commit-id-short :string})
                  #_#_r/renderers       (assoc r/renderers ::x RenderName)]
          (r/RenderGrid.
            {::r/row-height-px 25
             ::r/max-height-px "100%"
             ::r/columns       [{::r/attribute :dustingetz.datafy-git/commit-id-short}
                                {::r/attribute :msg}]}
            nil nil
            (e/fn* []
              (r/InputFilter. :msg
                (r/Nav. (datafy repo) :log))))))
      (catch Pending _))))

(e/defn GitBrowser [& [path]]
  (e/server
    (let [path (or path ".")
          repo (load-repo path)]
      (GitLog. repo))))