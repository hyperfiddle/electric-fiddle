(ns dustingetz.hnbrowser
  (:import [hyperfiddle.electric Pending])
  (:require [clojure.datafy :refer [datafy]]
            [datagrid.datafy-renderer :as r]
            [datagrid.file-explorer :refer [RouterInput]]
            [datagrid.schema :as schema]
            [dustingetz.datafy-hn :as hn]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-css :as css]
            [hyperfiddle.electric-dom2 :as dom]))

(e/defn HackerNewsBrowser []
  (e/server
    (let [hn (hn/->HNClient "https://hacker-news.firebaseio.com/v0/")]
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
          #_(RouterInput. {} :msg)
          (e/server
            (binding [r/Render          r/SchemaRenderer
                      r/schema-registry (schema/registry
                                          {})]
              (r/RenderGrid.
                {::r/row-height-px 25
                 ::r/max-height-px "100%"
                 ::r/columns       [{::r/attribute ::hn/title}
                                    {::r/attribute ::hn/by}]}
                nil nil
                (e/fn* []
                  #_(r/InputFilter. :msg)
                  (r/Nav. (datafy hn) ::hn/topstories)))))
          (catch Pending _))))))
