(ns datagrid.dialog
  (:require [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            #?(:cljs [hyperfiddle.electric-dom2.batch])
            [heroicons.electric.v24.outline :as i]
            [datagrid.stage :as stage])
  #?(:cljs (:require-macros [datagrid.dialog])))

(e/def !open?)
(e/def open? false)
(e/def close! (constantly nil))
(e/def on-close (constantly nil))
(e/def !features (atom #{}))

(e/def label-id)
(e/def description-id)

(e/defn* RegisterFeature [feat]
  (swap! !features conj feat)
  (e/on-unmount #(swap! !features disj feat)))

(defmacro title [{::keys [as] :or {as 'hyperfiddle.electric-dom2/h3}} & body]
  `(~as (dom/props {:id    label-id
                    :style {:grid-area "title"}})
    (new RegisterFeature ::labelled)
    ~@body))

(defmacro button-close [{::keys [as] :or {as 'hyperfiddle.electric-dom2/button}} & body]
  `(~as
    (dom/props {:type "reset" :style {:grid-area "close", :justify-self :end}})
    (dom/on! "click" stage/discard!)
    (i/x-mark (dom/props {:style {:width "1rem", :height "1rem", :cursor :pointer}}))
    ~@body))

(defmacro button-discard [{::keys [as] :or {as 'hyperfiddle.electric-dom2/button}} & body]
  `(~as
    (dom/props {:type "reset"})
    (dom/on! "click" stage/discard!)
    ~@body))

(defmacro button-commit [{::keys [as] :or {as 'hyperfiddle.electric-dom2/button}} & body]
  `(~as
    (dom/props {:type "submit"})
    (dom/on "click" (e/fn* [_] (stage/Commit.)))
    ~@body))

(defmacro description [{::keys [as] :or {as 'hyperfiddle.electric-dom2/p}} & body]
  `(~as
    (dom/props {:id description-id, :style {:grid-area "description"}})
    (new RegisterFeature ::described)
    ~@body))

(defmacro content [{::keys [as] :or {as 'hyperfiddle.electric-dom2/div}} & body]
  `(~as
    (dom/props {:style {:grid-area "content"}})
    (stage/stage! (do ~@body))))

(defmacro actions [{::keys [as] :or {as 'hyperfiddle.electric-dom2/div}} & body]
  `(~as (dom/props {:class "flex justify-end gap-2", :style {:grid-area "actions"}})
    ~@body))

(e/defn* Panel [Body]
  (e/client
    (dom/form
      (dom/props {:method :dialog
                  :class  "grid gap-2"
                  :style  {:grid-template-columns "1fr auto"
                           :grid-template-areas   " \"title close\" \"description description\" \"content content\" \"actions actions\" "}})
      (let [Commit stage/Commit]
        (binding [stage/Commit (e/fn* []
                                 (case (Commit.) ; sequence effects
                                   (stage/discard!)))]
          (Body.))))))

(defmacro panel [& body]
  `(new Panel (e/fn* [] ~@body)))

#?(:cljs
   (defn reset-form [^js node]
     (if (exists? (.-reset node))
       (.reset node)
       (when-let [^js form (first (filter #(= "FORM" (.-tagName %)) (.-children node)))]
         (.reset form)))))

#?(:cljs
   (defn set-open-state [node discard! modal? open?]
     (hyperfiddle.electric-dom2.batch/schedule!
       #(if open?
          (if modal?
            (.showModal node)
            (.show node))
          (do (discard!)
              (.close node))))))

(e/defn* Dialog [{::keys [modal? OnSubmit]
                  :or {OnSubmit `(e/fn* [x#] x#)}}
                 Body]
  (stage/staged OnSubmit
    (binding [label-id       (str (gensym "label_"))
              description-id (str (gensym "description_"))
              !features      (atom #{})]
      (e/client
        (dom/dialog
          (dom/props {:aria-role :dialog
                      :style     {:cursor :auto, :position :absolute}})
          (when modal?
            (dom/props {:aria-modal true}))
          (let [features (e/watch !features)]
            (when (features ::labelled)
              (dom/props {:aria-labelledby label-id,}))
            (when (features ::described)
              (dom/props {:aria-describedby description-id})))
          (binding [close! #(on-close)]
            (binding [stage/discard! (fn [] (reset-form dom/node) (stage/discard!) (close!))]
              (set-open-state dom/node stage/discard! modal? open?)
              (dom/on! "close" (fn [e]
                                 (.preventDefault e) ; important ! fire before form submit
                                 (close!)))
              (dom/on! js/document.body "click"
                (fn [^js e]
                  (when-let [target (.-target e)]
                    (when (or (= dom/node target)
                            (not (.contains dom/node target)))
                      (.close dom/node)))))
              (dom/on! "keyup" (fn [e] (when (= "Escape" (.-key e)) (.close dom/node))))
              (Body.))))))))

(defmacro dialog [{::keys [modal? OnSubmit] :as props} & body]
  `(new Dialog ~props (e/fn* [] ~@body)))

(defn event-happened-in-child-dialog? [current-node event]
  (.contains current-node (.. event -target (closest "dialog"))))

(defmacro anchor [{::keys [as] :or {as 'hyperfiddle.electric-dom2/button}} & body]
  `(~as
    (dom/props {:type :button, :style {:position :relative}})
    (dom/on! dom/node "click" (fn [^js e#]
                                (.preventDefault e#)
                                (.stopPropagation e#)
                                (swap! !open? not)))
    ;; (dom/on! dom/node "mouseenter" (fn [^js e#] (.preventDefault e#) (.stopPropagation e#) (reset! !open? true)))
    ;; (dom/on! dom/node "mouseleave" (fn [^js e#] (.preventDefault e#) (.stopPropagation e#) (reset! !open? false)))
    (dom/on! "keyup" (fn [^js e#]
                       (when (event-happened-in-child-dialog? dom/node e#)
                         (.preventDefault e#))))
    ~@body))

(defmacro controller [{::keys [open?] :or {open? false}} & body]
  `(binding [!open? (atom ~open?)]
     (binding [open? (e/watch !open?)
               on-close #(reset! !open? false)]
       ~@body)))


#?(:cljs
   (defn find-scrolling-parent [element]
     (loop [element element]
       (when-not (= js/document.documentElement element)
         (let [style (js/getComputedStyle element)]
           (if (some #{"scroll" "auto"} [(.-overflow style) (.-overflowX style) (.-overflowY style)])
             element
             (recur (.-parentNode element))))))))

#?(:cljs
   (defn vertically-clipped? [element]
     (> (.-bottom (.getBoundingClientRect element))
       (.-bottom (.getBoundingClientRect (find-scrolling-parent element))))))
