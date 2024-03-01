(ns datagrid.fiddles
  (:require
   #?(:clj [clojure.java.io :as io])
   #?(:clj [clojure.java.shell :as shell])
   #?(:clj [clojure.spec.alpha :as s]
      :cljs [cljs.spec.alpha :as s])
   [clojure.string :as str]
   #?(:clj [contrib.str])
   #?(:clj [contrib.datafy-fs :as dfs])
   [datagrid.collection-editor :as ce]
   [datagrid.datagrid :as dg]
   [datagrid.spinner :as spinner]
   [datagrid.styles :as styles]
   [datagrid.stage :as stage]
   [datagrid.virtual-scroll :as vs]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [hyperfiddle.electric-ui4 :as ui])
  (:import [hyperfiddle.electric Pending]))

;;; Hosts file manipulation

(def WHITE-SPACES #"[\p{Zs}\s]*") ; \p{Zs} means all space-like chars (e.g. NBSP, list: https://jkorpela.fi/chars/spaces.html)
(def WHITE-SPACES+ #"[\p{Zs}\s]+")
(defn blank-string? [str] (and (string? str) (boolean (re-matches WHITE-SPACES str))))
(defn read-tokens [str] (map first (re-seq #"(\S+)|([\p{Zs}\s]+)" str))) ; \S is equivalent to [^\s]

(s/def ::blank blank-string?)
(s/def ::token (s/and string? #(re-matches #"\S+" %)))
(s/def ::ip-v6 (s/and string? #(re-matches #"([a-f0-9:]+:+)+[a-f0-9]+" (str/lower-case %))))
(s/def ::ip-v4 (s/and string? #(re-matches #"[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}" %)))
(s/def ::ip (s/or :v4 ::ip-v4, :v6 ::ip-v6))
(s/def ::alias (s/or :hostname ::token, :blank ::blank))

(defn conform-entry [value] (s/conform (s/cat :ip ::ip, :aliases (s/* ::alias)) (read-tokens value)))
(defn unform-entry [{:keys [ip aliases]}] (str (second ip) (str/join "" (map second aliases))))
(s/def ::entry (s/conformer conform-entry unform-entry))

(defn blank-line? [str]
  (or (str/blank? str)
    (boolean (re-matches WHITE-SPACES+ str))))

(defn comment? [str] (re-matches #"^\s*#.*$" str))

(defn entry? [str] (s/valid? ::entry str))
(defn commented-entry? [str]
  (and (comment? str)
    (entry? (str/replace-first str #"^\s*#\s+" ""))))

(defn line-type [str]
  (cond
    (commented-entry? str) ::commented-entry
    (entry? str)           ::entry
    (comment? str)         ::comment
    :else                  ::raw))

(defn line-ip [str]
  (let [segments (str/split str WHITE-SPACES+ 3)]
    (if (= "#" (first segments))
      (second segments)
      (first segments))))

(defn set-line-ip [row-str ip-str]
  (let [segments (str/split row-str WHITE-SPACES+ 3)]
    (if (= "#" (first segments))
      (str "# " ip-str " " (nth segments 2))
      (str ip-str " " (second segments)))))

(defn line-hostnames [str]
  (last (str/split str WHITE-SPACES+ (if (str/starts-with? str "#") 3 2))))

(defn set-line-hostnames [row-str hostnames-str]
  (if (str/starts-with? row-str "#")
    (str "# " (line-ip row-str) " " hostnames-str)
    (str (line-ip row-str) " " hostnames-str)))

(defn toggle-comment [line]
  (cond (entry? line)           (str "# " line)
        (commented-entry? line) (str/replace-first line #"[\s#]*" "")
        :else                   line))

#?(:clj
   (defrecord HostsFileEntry [^String line]
     ccp/Datafiable
     (datafy [this]
       (case (line-type line)
         (::comment ::raw)
         line
         (::entry ::commented-entry)
         (let [ip            (line-ip line)
               hostnames     (line-hostnames line)
               hostnames-seq (re-seq #"\w+" hostnames)
               InetAdress    (java.net.InetAddress/getByName ip)]
           (with-meta
             {::ip ip, ::hostnames hostnames}
             {`ccp/nav
              (fn [xs k v]
                (case k
                  ::ip        InetAdress
                  ::hostnames hostnames-seq))}))))))

#?(:clj
   (defn read-hosts-file
     ([] (read-hosts-file (io/file "/etc/hosts")))
     ([^File file]
      (when (.exists file)
        (slurp file)))))

(defn copy-command [source-path destination-path] (str "cat " source-path " | sudo dd of=" destination-path))
(defn sudo-command [command] (str "/usr/bin/osascript -e 'do shell script \"" command " 2>&1 \" with administrator privileges'"))
#?(:clj
   (defn run-command! [command] (shell/sh "bash" "-c" command)))

#?(:clj
   (defn write-hosts-file! [content-str]
     (when content-str
       (let [source-path "/tmp/electric_hosts_editor.hosts"
             target-path "/etc/hosts"
             command (sudo-command (copy-command source-path target-path))]
         (spit source-path content-str)
         (println "Will run command with sudo rights: " command)
         (run-command! command)))))

;; ------

(e/def loading? false)

(e/defn RowChangeMonitor [{::keys [rows OnChange]} Body]
  (e/client
    (let [!loading? (atom false)]
      (binding [loading? (e/watch !loading?)]
        (e/server
          (let [output-rows (Body. rows)]
            (when (not= rows output-rows)
              (try (OnChange. output-rows)
                   nil
                   (catch Pending _
                     (e/client
                       (reset! !loading? true)
                       (e/on-unmount #(reset! !loading? false))))))))))))

(e/defn* CellInput [value OnCommit]
  (e/client
    (stage/staged OnCommit
      (dom/input (dom/props {:type :text :class "cell-input"})
                 (set! (.-value dom/node) value)
                 (dom/on "blur" (e/fn* [_] (when (some? stage/stage) (stage/Commit. stage/stage))))
                 (dom/on! "keydown" (fn [^js e]
                                      (case (.-key e)
                                        ("Enter" "Tab" "Escape") (.preventDefault e)
                                        nil)
                                      (let [direction (if (.-shiftKey e) ::dg/backwards ::dg/forwards)]
                                        (case (.-key e)
                                          "Enter"  (dg/focus-next-input ::dg/vertical direction dom/node)
                                          "Tab"    (dg/focus-next-input ::dg/horizontal direction dom/node)
                                          "Escape" (do (stage/discard!)
                                                       (set! (.-value dom/node) value)
                                                       (.focus (.closest dom/node "table")))
                                          nil))))
                 (dom/on! "input" (fn [^js e] (stage/stage! (.. e -target -value))))))))

(e/defn HostsGrid [rows OnChange]
  (e/server
    (RowChangeMonitor. {::rows rows, ::OnChange OnChange}
      (e/fn* [rows]
        (let [{::ce/keys [rows change!]} (ce/CollectionEditor. (vec (map-indexed vector rows)))]
          (e/client
            (vs/virtual-scroll {::vs/row-height  30
                                ::vs/padding-top 30
                                ::vs/rows-count  (e/server (count rows))}
              (dom/props {:style {:max-height (str (* 15 30) "px")}})
              (dg/datagrid {::dg/row-height 30}
                (dom/props {:tabIndex "1", :class [(styles/GridStyle.) (styles/CellsStyle.)]})
                (dg/header
                  (dg/row
                    (dg/column {::dg/frozen? true}
                      (dom/props {:style {:grid-column 1, :min-width "3ch", :width :min-content}})
                      (when (or loading? dg/loading?)
                        (spinner/Spinner. {})))
                    (dg/column {}
                      (dom/props {:style {:grid-column 2, :width :fit-content, :min-width "15ch", :font-size "1rem" :padding "0 1rem", :resize :horizontal}})
                      (dom/text "IP"))
                    (dg/column {}
                      (dom/props {:style {:grid-column 3, :font-size "1rem" :padding "0 1rem", :resize :horizontal}})
                      (dom/text "Hosts"))))
                (e/server
                  (vs/Paginate. rows
                    (e/fn* [[idx row]]
                      (let [line-type (line-type row)]
                        (e/client
                          (dg/row
                            (dg/cell (dom/props {:class "checkbox-cell"})
                                     (when (#{::entry ::commented-entry} line-type)
                                       (let [checked? (= ::entry line-type)]
                                         (ui/checkbox checked?
                                             (e/fn* [checked?]
                                               (e/server
                                                 (change! idx [idx (toggle-comment (e/snapshot row))])))
                                             (dom/props {:checked checked?})))))
                            (case line-type
                              (::entry ::commented-entry)
                              (do (dg/cell (dom/props {:class "entry-cell"
                                                       :style {:width :fit-content}})
                                           (CellInput. (line-ip row)
                                             (e/fn* [new-value]
                                               (e/server
                                                 (change! idx [idx (set-line-ip row new-value)])))))
                                  (dg/cell (dom/props {:class "entry-cell"})
                                           (CellInput. (line-hostnames row)
                                             (e/fn* [new-value]
                                               (e/server
                                                 (change! idx [idx (set-line-hostnames row new-value)]))))))
                              ;; else
                              (dg/cell (dom/props {:class "entry-cell"
                                                   :style {:grid-column "2 / 4"}})
                                       (CellInput. row
                                         (e/fn* [new-value]
                                           (e/server
                                             (change! idx [idx new-value])))))))))))))))
          (map second rows))))))

(e/defn HostFile-Editor []
  (e/client
    (dom/h1 (dom/text "/etc/hosts editor"))
    (e/server
      (let [!file-content (atom (read-hosts-file))
            lines (str/split-lines (e/watch !file-content))]
        (try
          (HostsGrid. lines
            (e/fn* [edited-lines]
              (let [new-file-content (str/join "\n" edited-lines)]
                #_(case (e/offload-task #(write-hosts-file! new-file-content)))
                (println new-file-content)
                (reset! !file-content new-file-content #_(read-hosts-file)))))
          (catch Pending _))))))

;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/def fiddles {`HostsFile-Editor HostFile-Editor})

;; Prod entrypoint, called by `prod.clj`
(e/defn FiddleMain [ring-request]
  (e/server
    (binding [e/http-request ring-request] ; make ring request available through the app
      (e/client
        (binding [dom/node js/document.body] ; where to mount dom elements
          (HostFile-Editor.))))))


