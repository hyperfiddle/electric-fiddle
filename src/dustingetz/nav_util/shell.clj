(ns dustingetz.nav-util.shell
  "Shell command wrappers and text parsing utilities for nav files."
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.java.shell :as sh]
   [clojure.string :as str]))

(defn sh-raw
  "Run a shell command, return raw stdout on exit 0, or nil on failure.
   Use when whitespace/newlines matter (multi-line output, fixed-width columns)."
  [& args]
  (try
    (let [{:keys [exit out]} (apply sh/sh args)]
      (when (zero? exit) out))
    (catch Exception _ nil)))

(defn sh-str
  "Run a shell command, return trimmed stdout on exit 0, or nil on failure."
  [& args]
  (when-let [out (apply sh-raw args)]
    (str/trim out)))

(defn sh-json
  "Run a shell command, parse stdout as JSON (keyword keys). Returns parsed data or nil."
  [& args]
  (when-let [out (apply sh-raw args)]
    (when-not (str/blank? out)
      (try
        (json/read-str out :key-fn keyword)
        (catch Exception _ nil)))))

(defn parse-kv-lines
  "Parse text lines of 'key<sep>value' format into a map.
   sep is a string (e.g. \":\" or \"=\" or \": \").
   Blank lines and unparseable lines are skipped."
  [text sep]
  (when text
    (->> (str/split-lines text)
      (remove str/blank?)
      (map (fn [line]
             (let [idx (.indexOf ^String line ^String sep)]
               (when (pos? idx)
                 [(str/trim (subs line 0 idx))
                  (str/trim (subs line (+ idx (count sep))))]))))
      (remove nil?)
      (into {}))))

(defn sh-lines
  "Run a shell command, return non-blank output lines as a vector.
   When skip-header is true, drops the first line (column headers).
   Returns nil on failure."
  [skip-header & args]
  (when-let [out (apply sh-raw args)]
    (let [lines (cond-> (str/split-lines out)
                  skip-header rest)]
      (vec (remove str/blank? lines)))))

(defn sh-json-lines
  "Run a shell command, parse each stdout line as JSON (keyword keys).
   For CLIs that emit newline-delimited JSON (e.g. docker --format '{{json .}}').
   Returns a vector of parsed maps, or nil on failure."
  [& args]
  (when-let [out (apply sh-raw args)]
    (->> (str/split-lines out)
      (remove str/blank?)
      (mapv #(try (json/read-str % :key-fn keyword) (catch Exception _ nil)))
      (filterv some?))))

(defn parse-columns
  "Split a trimmed line by whitespace into at most n columns.
   The last column captures the remainder (preserving internal spaces)."
  [line n]
  (str/split (str/trim line) #"\s+" n))

(defn read-file-lines
  "Read a file, return non-blank lines with leading comment lines removed.
   comment-prefix defaults to \"#\". Returns nil if file doesn't exist."
  ([path] (read-file-lines path "#"))
  ([path comment-prefix]
   (try
     (let [f (io/file path)]
       (when (.exists f)
         (->> (str/split-lines (slurp f))
           (map str/trim)
           (remove str/blank?)
           (remove #(str/starts-with? % comment-prefix))
           vec)))
     (catch Exception _ nil))))

(defn format-bytes
  "Format a byte count as a human-readable string (B/KB/MB/GB)."
  [n]
  (cond
    (nil? n) "n/a"
    (neg? n) "n/a"
    (< n 1024) (str n "B")
    (< n (* 1024 1024)) (str (quot n 1024) "KB")
    (< n (* 1024 1024 1024)) (str (quot n (* 1024 1024)) "MB")
    :else (str (quot n (* 1024 1024 1024)) "GB")))
