(ns datagrid.file-explorer
  (:require [clojure.datafy :refer [datafy]]
            #?(:clj [clojure.java.io :as io])
            [contrib.datafy-fs #?(:clj :as, :cljs :as-alias) dfs]
            [datagrid.datafy-renderer :as r]
            [datagrid.schema :as schema]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-css :as css]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.router :as router])
  (:import [hyperfiddle.electric Pending]))

(e/defn RenderName [props e a V]
  (let [[[e a V]  [e-1 a-1 V-1]] r/stack
        row                    (V-1.)
        absolute-path          (::dfs/absolute-path row)
        v                      (V.)]
    (e/client
      (router/link ['.. (list `FileExplorer absolute-path)] (dom/text v)))))

(e/defn RouterInput [props attribute]
  (e/client
    (r/RouterStorage. attribute
      (e/fn* [value]
        (r/RenderInput. props attribute value)))))

(e/defn DirectoryViewer [file]
  (e/client
    (dom/props {:style {:height         "100dvh"
                        :padding-bottom 0
                        :box-sizing     :border-box
                        :margin         0
                        :display        :flex
                        :flex-direction :column}
                :class [(css/scoped-style
                          (css/rule ".virtual-scroll" {:flex 1}))]})
    (try
      (RouterInput. {::dom/type :search} ::dfs/name)
      (e/server
        (binding [r/Render          r/SchemaRenderer
                  r/schema-registry (schema/registry
                                      {::dfs/content   [:sequential {:cardinality :many} :any] ; FIXME specify :any, recursive?
                                       ::dfs/name      :string
                                       ::dfs/children  [:sequential {:cardinality :many} :any] ; FIXME specify :any, recursive?
                                       ::dfs/accessed  :string
                                       ::dfs/modified  :string
                                       ::dfs/created   :string
                                       ::dfs/size      :string
                                       ::dfs/mime-type :string
                                       ::dfs/kind      :string})
                  r/renderers       (assoc r/renderers ::dfs/name RenderName)]
          (r/RenderGrid.
            {::r/row-height-px 25
             ::r/max-height-px "100%"
             ::r/columns       [{::r/attribute ::dfs/name}
                                {::r/attribute ::dfs/size}
                                {::r/attribute ::dfs/mime-type}]}
            nil nil
            (e/fn* []
              (r/InputFilter. (comp ::dfs/name datafy) ::dfs/name ; FIXME missing a datafy call
                (r/Nav. (datafy file) ::dfs/children))))))
      (catch Pending _))))

(e/defn FileViewer [file]
  (e/server
    (binding [r/Render          r/SchemaRenderer
              r/schema-registry (schema/registry {::dfs/name :string})
              #_#_r/renderers       (assoc r/renderers ::dfs/file r/RenderForm, ::dfs/name RenderName)]
      (r/RenderForm. {::r/row-height-px 25
                      ::r/max-height-px "100%"}
        nil nil (e/fn* [] file)))))

(e/defn FileExplorer [& [path]]
  (e/server
    (let [path (or path ".")
          file (io/as-file path)]
      (if-not (.exists file)
        (e/client
          (dom/p (dom/text "No such file " path)))
        (if (.isDirectory file)
          (DirectoryViewer. file)
          (FileViewer. file))))))
