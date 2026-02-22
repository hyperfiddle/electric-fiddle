(ns dustingetz.nav-disk
  "Disk and filesystem browser via java.nio.file.FileStore and df."
  (:require
   [clojure.java.shell :as sh]
   [clojure.string :as str]
   [dustingetz.nav-util.shell :as shell]
   [hyperfiddle.hfql2 :as hfql :refer [hfql]]
   [hyperfiddle.hfql2.protocols :refer [Identifiable Suggestable -hfql-resolve]])
  (:import
   [java.nio.file FileSystems]))

;; -- Records --

(defrecord DiskStore [name store-type total-bytes usable-bytes used-percent read-only])
(defrecord DiskPartition [device mount-point filesystem size used available use-percent])

;; -- Data functions --

(defn file-stores
  "All mounted file stores from java.nio.file.FileSystems with space usage."
  []
  (try
    (->> (.getFileStores (FileSystems/getDefault))
      iterator-seq
      (mapv (fn [fs]
              (let [total (.getTotalSpace fs)
                    usable (.getUsableSpace fs)
                    pct (if (pos? total) (int (* 100 (/ (- total usable) total))) 0)]
                (->DiskStore (.name fs) (.type fs)
                  (shell/format-bytes total) (shell/format-bytes usable)
                  (str pct "%") (.isReadOnly fs))))))
    (catch Exception _ [])))

(defn partitions
  "Disk partitions and mount points from `df -h`."
  []
  (try
    (let [{:keys [exit out]} (sh/sh "df" "-h")]
      (when (zero? exit)
        (->> (rest (str/split-lines out))
          (remove str/blank?)
          (mapv (fn [line]
                  (let [parts (str/split (str/trim line) #"\s+" 9)]
                    (when (>= (count parts) 6)
                      (->DiskPartition
                        (nth parts 0) (last parts)
                        "" (nth parts 1) (nth parts 2)
                        (nth parts 3) (nth parts 4))))))
          (remove nil?))))
    (catch Exception _ [])))

(defn find-store
  "Single file store by name from java.nio.file.FileSystems."
  [name]
  (first (filter #(= name (:name %)) (file-stores))))

;; -- Named accessors --

(defn store-display-name [s] (:name s))
(defn partition-mount [p] (:mount-point p))

;; -- Protocol extensions --

(extend-type DiskStore
  Identifiable (-identify [s] `(find-store ~(:name s)))
  Suggestable (-suggest [_] (hfql [:name :store-type :total-bytes :usable-bytes :used-percent :read-only])))

(extend-type DiskPartition
  Identifiable (-identify [_] `(partitions))
  Suggestable (-suggest [_] (hfql [:device :mount-point :filesystem :size :used :available :use-percent])))

;; -- Sitemap --

(def sitemap
  {`file-stores
   (hfql {(file-stores) {* ^{::hfql/select `find-store}
                            [^{::hfql/link `(find-store ~'%)} store-display-name
                             :store-type :total-bytes :usable-bytes :used-percent]}})

   `partitions
   (hfql {(partitions) {* [partition-mount :device :size :used :available :use-percent]}})

   `find-store
   (hfql [:name :store-type :total-bytes :usable-bytes :used-percent :read-only])})

;; -- Resolvers --

(defmethod -hfql-resolve `file-stores [_] (file-stores))
(defmethod -hfql-resolve `partitions [_] (partitions))
(defmethod -hfql-resolve `find-store [[_ name]] (find-store name))
