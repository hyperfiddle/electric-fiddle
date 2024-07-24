(ns agents.fiddles
  (:require
   [contrib.str]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [agents.agents :as agents]
   [agents.ports :as ports]
   [agents.browser]
   [clojure.edn :as edn]))

(def !args (atom [1000]))

(comment (reset! !args [1000 5]))

(defn args-parser []
  (let [!last (atom [nil nil])]
    (fn [args-str]
      (try (let [v (edn/read-string args-str)]
             (if (vector? v)
               (swap! !last assoc 0 v 1 nil)
               (swap! !last assoc 1 (str "Invalid arg vector " (pr-str v)))))
           (catch #?(:clj Throwable, :cljs :default) err
             (swap! !last assoc 1 (ex-message err))))
      @!last)))

(def type->pred {:int int?})

(defn arg-parser [type-or-pred]
  (let [!last (atom [nil nil])
        matches? (type->pred type-or-pred type-or-pred)]
    (fn [value]
      (try (let [v (edn/read-string value)]
             (if (matches? v)
               (swap! !last assoc 0 v 1 nil)
               (swap! !last assoc 1 (str "Invalid value " (pr-str v)))))
           (catch #?(:clj Throwable, :cljs :default) err
             (swap! !last assoc 1 (ex-message err))))
      @!last)))

(defn arg-input-placeholder [arg-type]
  (when (ident? arg-type)
    (str arg-type)))

(defn type->input-type [type]
  (get {:int :number} type :text))

(e/defn ArgInput [[arg-name {:keys [type default]}]]
  (e/client
    (dom/dd
      (let [parse                   (arg-parser type)
            [^js input-node user-input] (dom/input (dom/props {:type        (type->input-type type)
                                                               :placeholder (str (name arg-name) " " (arg-input-placeholder type))})
                                                   (set! (.-value dom/node) default)
                                               [dom/node
                                                (or (dom/on! "input" #(.. % -target -value))
                                                  (str default))])
            [value error-message]   (parse user-input)]
        (set! (.. input-node -style -borderColor) (if error-message "red" "initial"))
        value))))

(e/defn RenderArgsForm [{::agents/keys [args] :as spec}]
  (e/client
    (when (seq args)
      (dom/dl
        (e/for-by first [[arg-name :as arg-spec] args]
          (dom/dt (dom/text (name arg-name)))
          (ArgInput. arg-spec))))))

(e/defn Dashboard []
  (e/client
    (binding [dom/node js/document.body] ; where to mount dom elements
      (dom/h1 (dom/text "Dashboard"))
      (dom/ul
        (e/server
          (e/for-by ::agents/id [{::agents/keys [id functions specs] :as agent} (vals (e/watch agents/!agents))]
            (e/client
              (dom/li
                (dom/h2 (dom/text id))
                (dom/pre (dom/text (contrib.str/pprint-str agent)))
                (dom/h3 (dom/text "Functions"))
                (dom/ul
                  (e/server
                    (e/for-by key [[fsym _] functions]
                      (e/client
                        (dom/li
                          (let [on? (dom/input
                                      (dom/props {:type :checkbox})
                                      ;; (set! (.-checked dom/node) running?)
                                      (dom/on! "change" #(.-checked (.-target %))))]
                            (dom/text "( " fsym "  ")
                            (let [args (RenderArgsForm. (get specs fsym))]
                              (dom/text " )")
                              (when on?
                                (e/server
                                  (let [result (ports/Call. fsym args)]
                                    (e/client
                                      (dom/pre (dom/text result)))))))))))))))))))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/def fiddles {`Dashboard Dashboard})

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [_ring-request] (e/server (Dashboard.)))
