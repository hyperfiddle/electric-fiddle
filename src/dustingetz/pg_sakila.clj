(ns dustingetz.pg-sakila
  #_{org.postgresql/postgresql {:mvn/version "42.7.3"}
     com.github.seancorfield/next.jdbc {:mvn/version "1.3.955"}}
  (:require [next.jdbc :as jdbc]
            [hyperfiddle.rcf :refer [tests]]))

;which pg_ctl
;/opt/homebrew/bin/pg_ctl
;cd src/hf/
;initdb --locale=C -E UTF-8 ./postgres
;pg_ctl -D ./postgres -l logfile start
;git clone git@github.com:jOOQ/sakila.git
;dropdb sakila
;createdb sakila
;createuser -s postgres     # fix error in next step
;psql -d sakila -f sakila/postgres-sakila-db/postgres-sakila-schema.sql
;psql -d sakila -f sakila/postgres-sakila-db/postgres-sakila-insert-data-using-copy.sql
;psql -d sakila -c "SELECT first_name, last_name FROM actor LIMIT 5;"
;psql -d sakila
;SELECT first_name, last_name, count(*) films FROM actor AS a
;JOIN film_actor AS fa USING (actor_id)
;GROUP BY actor_id, first_name, last_name
;ORDER BY films DESC
;LIMIT 1;

(def test-config {:dbtype "postgresql" :dbname "sakila" :host "localhost" :port 5432})
(def test-conn (delay (jdbc/get-connection test-config)))

(tests
  (require '[clojure.datafy :refer [datafy nav]]))

(tests
  (jdbc/execute! @test-conn ["SELECT first_name, last_name FROM actor LIMIT 3;"])
  := [#:actor{:first_name "SCARLETT", :last_name "DAMON"}
      #:actor{:first_name "ANGELA", :last_name "WITHERSPOON"}
      #:actor{:first_name "RUSSELL", :last_name "TEMPLE"}]

  (jdbc/execute! @test-conn
    ["SELECT first_name, last_name, count(*) films FROM actor AS a
     JOIN film_actor AS fa USING (actor_id)
     GROUP BY actor_id, first_name, last_name ORDER BY films DESC LIMIT 1;"])
  := [{:actor/first_name "GINA", :actor/last_name "DEGENERES", :films 42}])

(tests
  "nav over FKs"
  ; https://github.com/seancorfield/next-jdbc/blob/develop/doc/datafy-nav-and-schema.md
  (def x (jdbc/execute-one! @test-conn ["select * from film limit 1;"]
           {:schema {:film/language_id :language/language_id}}))
  (type x) := clojure.lang.PersistentHashMap
  (:film/language_id x) := 1
  (nav x :film/language_id (:film/language_id x))
  := #:language{:language_id 1,
                :name "English             ",
                :last_update #inst"2006-02-15T10:02:19.000000000-00:00"})