(ns datagrid.host-viewer
  (:require [clojure.core.protocols :as ccp]
            [clojure.datafy :refer [datafy nav]]
            #?(:clj [clojure.java.io :as io])
            #?(:clj [clojure.java.shell :as shell])
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [contrib.datafy-fs #?(:clj :as, :cljs :as-alias) dfs]
            [datagrid.datafy-renderer :as r]
            [datagrid.datagrid :as dg]
            [datagrid.schema :as schema]
            [datagrid.virtual-scroll :as vs]
            [datagrid.spinner :as spinner]
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom])
  #?(:clj (:import java.io.File)))

;;; Hosts file parsing and editing

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
     (datafy [_this]
       (let [type (line-type line)]
         (case type
           (::comment ::raw)
           {::type ::text, ::text line}
           (::entry ::commented-entry)
           (let [ip            (line-ip line)
                 hostnames     (line-hostnames line)
                 hostnames-seq (map first (re-seq #"(\w+|\.)+" hostnames))]
             (with-meta
               {::type ::entry, ::ip ip, ::hostnames hostnames, ::enabled? (= ::entry type)}
               {`ccp/nav
                (fn [_xs k v]
                  (case k
                    ::ip        ip
                    ::hostnames hostnames-seq ; TODO render as taglist
                    v))})))))))

#?(:clj
   (defn read-hosts-file
     ([] (read-hosts-file (io/file "/etc/hosts")))
     ([^File file]
      (when (.exists file)
        (as-> (datafy file) %
          (nav % ::dfs/content ())
          (map ->HostsFileEntry %))))))

(defn copy-command [source-path destination-path] (str "cat " source-path " | sudo dd of=" destination-path))
(defn sudo-command [command] (str "/usr/bin/osascript -e 'do shell script \"" command " 2>&1 \" with administrator privileges'"))
#?(:clj
   (defn run-command! [command] (shell/sh "bash" "-c" command)))

#?(:clj
   (defn write-hosts-file! [content-str]
     (when content-str
       (let [source-path "/tmp/electric_hosts_editor.hosts"
             target-path "/etc/hosts"
             command     (sudo-command (copy-command source-path target-path))]
         (spit source-path content-str)
         (println "Will run command with sudo rights: " command)
         (run-command! command)))))

;;; UI

(e/defn RenderCustomRow [props e a V] ; to get a dynamic layout
  (let [e vs/index ; default row identifier is the virtual scroll row index
        V (e/share (e/fn* [] (r/JoinValue. (V.))))
        v (V.)]
    (case (::type v)
      ::text  (e/client
                (dg/row
                  (e/server
                    (r/RenderCell. {::dom/props {:style {:grid-column "2 / -1"}}}
                      e                     ; e
                      ::text                ; a
                      (e/fn* [] (::text v)) ; V
                      ))))
      ::entry (r/DefaultRowRenderer. props e a V)) ; fallback to default impl
    ))

(e/defn HostFile-Editor []
  (e/client
    (dom/h1 (dom/text "/etc/hosts viewer"))
    (e/server
      (let [!entries (atom (read-hosts-file))]
        (binding [r/Render          r/SchemaRenderer
                  r/RenderRow       RenderCustomRow
                  r/schema-registry (schema/registry
                                      {::enabled?    :boolean
                                       ::ip          :string
                                       ::text        :string
                                       ::hostnames   [:sequential {:cardinality :one} :string]
                                       ::dfs/content [:sequential {:cardinality :many} :any] ; FIXME specify :any
                                       ::dfs/name    :string})]
          (r/RenderGrid.
            {::r/row-height-px 25
             ::r/max-height-px (* 14 25) ; 15 25px tall lines
             ::r/columns       [{::r/attribute ::enabled?
                                 ::r/title     ""
                                 ::r/Body      (e/fn* []
                                                 (e/client
                                                   (when (or r/loading? dg/loading?)
                                                     (spinner/Spinner. {}))))}
                                {::r/attribute ::ip}
                                {::r/attribute ::hostnames}]
             ::dom/props       {:style {:grid-template-columns "min-content auto auto"}}}
            nil nil (e/fn* [] (e/watch !entries))))))))

#_
(let [new-file-content (str/join "\n" (map entry->line edited-entries))]
  #_(case (e/offload-task #(write-hosts-file! content-str)))
  (println new-file-content)
  (reset! !entries edited-entries #_(read-hosts-file)))
