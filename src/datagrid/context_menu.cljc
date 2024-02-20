(ns datagrid.context-menu
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom])
  #?(:cljs (:require-macros [datagrid.context-menu])))

(e/def open?) ; bool
(e/def mouse-coordinates)  ; [x y]
(e/def open!) ; (fn [context event] …)
(e/def close!) ; (fn [] …)
(e/def context-menu?)
(e/def context)

(e/defn* Item [Body]
  (e/client
    (dom/li
      (Body.))))

(defmacro item [& body]
  `(new Item (e/fn* [] ~@body)))

(e/defn* Items [Body]
  (e/client
    (when open?
      ;; (dom/div (dom/props {:class "absolute z-10 bg-red-500/10 w-full h-full"}))
      (dom/ul (dom/props {:style {:margin 0, :padding 0}})
        (if context-menu?
          (let [[left top] mouse-coordinates]
            (dom/props {:style {:position :absolute
                                :left (str left "px")
                                :top  (str top "px")}})))
        (Body.)))))

(defmacro items [& body]
  `(new Items (e/fn* [] ~@body)))

(e/defn* Menu [{::keys [open? context-menu?]} Body]
  (e/client
    (let [!open?             (atom open?)
          !context           (atom nil)
          !mouse-coordinates (atom [0 0])]
      (binding [datagrid.context-menu/open?         (e/watch !open?)
                datagrid.context-menu/context-menu? context-menu?
                context                                                  (e/watch !context)
                mouse-coordinates                                        (e/watch !mouse-coordinates)]
        (binding [open!  (fn [context ^js event]
                           (.preventDefault event)
                           (reset! !mouse-coordinates [(.-pageX event) (.-pageY event)])
                           (reset! !context context)
                           (reset! !open? true))
                  close! (fn [] (reset! !open? false))]
          (Body.))))))

(defmacro menu [{::keys [open? context-menu?] :as props} & body]
  `(new Menu ~props (e/fn* [] ~@body)))
