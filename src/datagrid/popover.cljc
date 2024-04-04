(ns datagrid.popover
  (:require
   [datagrid.dialog :as dialog]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.router :as router]))

(e/defn Popover [{::keys [path AnchorBody]} PopoverBody]
  (e/client
    (let [path     (router/resolve-path path)
          present? (contains? router/route (first path))]
      (dialog/controller {::dialog/open? present?}
        (dialog/anchor {::dialog/as dom/a}
          (dom/props {:style {:display :contents}
                      :href  (router/LinkHref. path)})
          (if AnchorBody (AnchorBody.) (dom/text (pr-str path))))
        (if dialog/open?
          (when-not present?
            (router/Navigate!. ['. (assoc-in router/route path nil)]))
          (when present?
            (router/Navigate!. ['. (dissoc router/route (first path))])))
        (dialog/dialog {::dialog/modal? true}
          (dom/props {:style {:z-index 1
                              :padding 0}})
          (dialog/panel
            (dialog/content {}
              (when dialog/open?
                (router/focus path
                  (PopoverBody.))))))))))

(defmacro popover [{::keys [path AnchorBody]} & body]
  `(Popover. {::path ~path, ::AnchorBody ~AnchorBody} (e/fn* [] ~@body)))
