(ns metabase.lib.breakout-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [medley.core :as m]
   [metabase.lib.core :as lib]
   [metabase.lib.dev :as lib.dev]
   [metabase.lib.test-metadata :as meta]
   [metabase.lib.test-util :as lib.tu]
   [metabase.lib.util :as lib.util]
   [metabase.util :as u]
   #?@(:cljs ([metabase.test-runner.assert-exprs.approximately-equal]))))

#?(:cljs (comment metabase.test-runner.assert-exprs.approximately-equal/keep-me))

(deftest ^:parallel query-name-with-breakouts-test
  (let [query (-> (lib/query-for-table-name meta/metadata-provider "CHECKINS")
                  (lib/aggregate (lib/count))
                  (lib/breakout (lib/with-temporal-bucket (lib/field (meta/id :checkins :date)) :year)))]
    (is (=? {:lib/type :mbql/query
             :database (meta/id)
             :stages   [{:lib/type     :mbql.stage/mbql
                         :source-table (meta/id :checkins)
                         :aggregation  [[:count {}]]
                         :breakout     [[:field
                                         {:base-type :type/Date, :temporal-unit :year}
                                         (meta/id :checkins :date)]]}]}
            query))
    (is (= "Checkins, Count, Grouped by Date (year)"
           (lib/display-name query query)
           (lib/describe-query query)
           (lib/suggested-name query)))))

(deftest ^:parallel breakouts-test
  (let [query (-> (lib/query-for-table-name meta/metadata-provider "CHECKINS")
                  (lib/breakout (lib/field (meta/id :checkins :date))))]
    (is (=? [[:field {} (meta/id :checkins :date)]]
            (lib/breakouts query)))))

(deftest ^:parallel breakout-should-drop-invalid-parts
  (let [query (-> (lib/query-for-table-name meta/metadata-provider "VENUES")
                  (lib/with-fields [(lib/field "VENUES" "PRICE")])
                  (lib/order-by (lib/field "VENUES" "PRICE"))
                  (lib/join (-> (lib/join-clause (meta/table-metadata :categories)
                                                 [(lib/=
                                                    (lib/field "VENUES" "CATEGORY_ID")
                                                    (lib/with-join-alias (lib/field "CATEGORIES" "ID") "Cat"))])
                                (lib/with-join-fields [(lib/field "CATEGORIES" "ID")])))
                  (lib/append-stage)
                  (lib/with-fields [(lib/field "VENUES" "PRICE")])
                  (lib/breakout 0 (lib/field "VENUES" "CATEGORY_ID")))
        first-stage (lib.util/query-stage query 0)
        first-join (first (lib/joins query 0))]
    (is (= 1 (count (:stages query))))
    (is (not (contains? first-stage :fields)))
    (is (not (contains? first-stage :order-by)))
    (is (= 1 (count (lib/joins query 0))))
    (is (not (contains? first-join :fields))))
  (testing "Already summarized query should be left alone"
    (let [query (-> (lib/query-for-table-name meta/metadata-provider "VENUES")
                    (lib/breakout (lib/field "VENUES" "CATEGORY_ID"))
                    (lib/order-by (lib/field "VENUES" "CATEGORY_ID"))
                    (lib/append-stage)
                    (lib/breakout 0 (lib/field "VENUES" "PRICE")))
          first-stage (lib.util/query-stage query 0)]
      (is (= 2 (count (:stages query))))
      (is (contains? first-stage :order-by)))))

(deftest ^:parallel breakoutable-columns-test
  (let [query (lib/query-for-table-name meta/metadata-provider "VENUES")]
    (testing (lib.util/format "Query =\n%s" (u/pprint-to-str query))
      (is (=? [{:lib/type                 :metadata/field
                :name                     "ID"
                :display_name             "ID"
                :id                       (meta/id :venues :id)
                :table_id                 (meta/id :venues)
                :base_type                :type/BigInteger
                :lib/source-column-alias  "ID"
                :lib/desired-column-alias "ID"}
               {:lib/type                 :metadata/field
                :name                     "NAME"
                :display_name             "Name"
                :id                       (meta/id :venues :name)
                :table_id                 (meta/id :venues)
                :base_type                :type/Text
                :lib/source-column-alias  "NAME"
                :lib/desired-column-alias "NAME"}
               {:lib/type                 :metadata/field
                :name                     "CATEGORY_ID"
                :display_name             "Category ID"
                :id                       (meta/id :venues :category-id)
                :table_id                 (meta/id :venues)
                :lib/source-column-alias  "CATEGORY_ID"
                :lib/desired-column-alias "CATEGORY_ID"}
               {:lib/type                 :metadata/field
                :name                     "LATITUDE"
                :display_name             "Latitude"
                :id                       (meta/id :venues :latitude)
                :table_id                 (meta/id :venues)
                :base_type                :type/Float
                :lib/source-column-alias  "LATITUDE"
                :lib/desired-column-alias "LATITUDE"}
               {:lib/type                 :metadata/field
                :name                     "LONGITUDE"
                :display_name             "Longitude"
                :id                       (meta/id :venues :longitude)
                :table_id                 (meta/id :venues)
                :base_type                :type/Float
                :lib/source-column-alias  "LONGITUDE"
                :lib/desired-column-alias "LONGITUDE"}
               {:lib/type                 :metadata/field
                :name                     "PRICE"
                :display_name             "Price"
                :id                       (meta/id :venues :price)
                :table_id                 (meta/id :venues)
                :base_type                :type/Integer
                :lib/source-column-alias  "PRICE"
                :lib/desired-column-alias "PRICE"}
               {:lib/type                 :metadata/field
                :name                     "ID"
                :display_name             "ID"
                :id                       (meta/id :categories :id)
                :table_id                 (meta/id :categories)
                :base_type                :type/BigInteger
                :lib/source-column-alias  "ID"
                :lib/desired-column-alias "CATEGORIES__via__CATEGORY_ID__ID"}
               {:lib/type                 :metadata/field
                :name                     "NAME"
                :display_name             "Name"
                :id                       (meta/id :categories :name)
                :table_id                 (meta/id :categories)
                :base_type                :type/Text
                :lib/source-column-alias  "NAME"
                :lib/desired-column-alias "CATEGORIES__via__CATEGORY_ID__NAME"}]
              (lib/breakoutable-columns query))))))

(deftest ^:parallel breakoutable-expressions-test
  (testing "orderable-columns should include expressions"
    (let [query (-> (lib/query-for-table-name meta/metadata-provider "VENUES")
                    (lib/expression "Category ID + 1"  (lib/+ (lib/field "VENUES" "CATEGORY_ID") 1)))]
      (testing (lib.util/format "Query =\n%s" (u/pprint-to-str query))
        (is (=? [{:id (meta/id :venues :id) :name "ID"}
                 {:id (meta/id :venues :name) :name "NAME"}
                 {:id (meta/id :venues :category-id) :name "CATEGORY_ID"}
                 {:id (meta/id :venues :latitude) :name "LATITUDE"}
                 {:id (meta/id :venues :longitude) :name "LONGITUDE"}
                 {:id (meta/id :venues :price) :name "PRICE"}
                 {:lib/type     :metadata/field
                  :base_type    :type/Integer
                  :name         "Category ID + 1"
                  :display_name "Category ID + 1"
                  :lib/source   :source/expressions}
                 {:id (meta/id :categories :id) :name "ID"}
                 {:id (meta/id :categories :name) :name "NAME"}]
                (lib/breakoutable-columns query)))))))

(deftest ^:parallel breakoutable-explicit-joins-test
  (testing "orderable-columns should include columns from explicit joins"
    (let [query (-> (lib/query-for-table-name meta/metadata-provider "VENUES")
                    (lib/join (-> (lib/join-clause
                                   (meta/table-metadata :categories)
                                   [(lib/=
                                      (lib/field "VENUES" "CATEGORY_ID")
                                      (lib/with-join-alias (lib/field "CATEGORIES" "ID") "Cat"))])
                                  (lib/with-join-alias "Cat")
                                  (lib/with-join-fields :all))))]
      (testing (lib.util/format "Query =\n%s" (u/pprint-to-str query))
        (is (=? [{:id (meta/id :venues :id) :name "ID"}
                 {:id (meta/id :venues :name) :name "NAME"}
                 {:id (meta/id :venues :category-id) :name "CATEGORY_ID"}
                 {:id (meta/id :venues :latitude) :name "LATITUDE"}
                 {:id (meta/id :venues :longitude) :name "LONGITUDE"}
                 {:id (meta/id :venues :price) :name "PRICE"}
                 {:lib/type     :metadata/field
                  :name         "ID"
                  :display_name "ID"
                  :source_alias "Cat"
                  :id           (meta/id :categories :id)
                  :table_id     (meta/id :categories)
                  :base_type    :type/BigInteger}
                 {:lib/type     :metadata/field
                  :name         "NAME"
                  :display_name "Name"
                  :source_alias "Cat"
                  :id           (meta/id :categories :name)
                  :table_id     (meta/id :categories)
                  :base_type    :type/Text}]
                (lib/breakoutable-columns query)))))))

(deftest ^:parallel breakoutable-columns-source-card-test
  (doseq [varr [#'lib.tu/query-with-card-source-table
                #'lib.tu/query-with-card-source-table-with-result-metadata]
          :let [query (varr)]]
    (testing (str (pr-str varr) \newline (lib.util/format "Query =\n%s" (u/pprint-to-str query)))
      (let [columns (lib/breakoutable-columns query)]
        (is (=? [{:name                     "USER_ID"
                  :display_name             "User ID"
                  :base_type                :type/Integer
                  :lib/source               :source/card
                  :lib/desired-column-alias "USER_ID"}
                 {:name                     "count"
                  :display_name             "Count"
                  :base_type                :type/Integer
                  :lib/source               :source/card
                  :lib/desired-column-alias "count"}
                 {:name                     "ID"
                  :display_name             "ID"
                  :base_type                :type/BigInteger
                  :lib/source               :source/implicitly-joinable
                  :lib/desired-column-alias "USERS__via__USER_ID__ID"}
                 {:name                     "NAME"
                  :display_name             "Name"
                  :base_type                :type/Text
                  :lib/source               :source/implicitly-joinable
                  :lib/desired-column-alias "USERS__via__USER_ID__NAME"}
                 {:name                     "LAST_LOGIN"
                  :display_name             "Last Login"
                  :base_type                :type/DateTime
                  :lib/source               :source/implicitly-joinable
                  :lib/desired-column-alias "USERS__via__USER_ID__LAST_LOGIN"}]
                columns))
        (testing `lib/display-info
          (is (=? [{:name                   "USER_ID"
                    :display_name           "User ID"
                    :table                  {:name "My Card", :display_name "My Card"}
                    :is_from_previous_stage false
                    :is_implicitly_joinable false}
                   {:name                   "count"
                    :display_name           "Count"
                    :table                  {:name "My Card", :display_name "My Card"}
                    :is_from_previous_stage false
                    :is_implicitly_joinable false}
                   {:name                   "ID"
                    :display_name           "ID"
                    :table                  {:name "USERS", :display_name "Users"}
                    :is_from_previous_stage false
                    :is_implicitly_joinable true}
                   {:name                   "NAME"
                    :display_name           "Name"
                    :table                  {:name "USERS", :display_name "Users"}
                    :is_from_previous_stage false
                    :is_implicitly_joinable true}
                   {:name                   "LAST_LOGIN"
                    :display_name           "Last Login"
                    :table                  {:name "USERS", :display_name "Users"}
                    :is_from_previous_stage false
                    :is_implicitly_joinable true}]
                  (for [col columns]
                    (lib/display-info query col)))))))))

(defn- breakout-column-excluded? [query column query']
  (let [breakoutable-columns  (lib/breakoutable-columns query)
        breakoutable-columns' (lib/breakoutable-columns query')]
    (= (disj (set breakoutable-columns) column) (set breakoutable-columns'))))

(deftest ^:parallel breakoutable-columns-e2e-test
  (testing "Use the metadata returned by `breakoutable-columns` to add a new breakout to a query."
    (let [query (lib/query-for-table-name meta/metadata-provider "VENUES")]
      (is (=? {:lib/type :mbql/query
               :database (meta/id)
               :stages   [{:lib/type     :mbql.stage/mbql
                           :source-table (meta/id :venues)
                           :lib/options  {:lib/uuid string?}}]}
              query))
      (testing (lib.util/format "Query =\n%s" (u/pprint-to-str query))
        (let [breakoutable-columns (lib/breakoutable-columns query)
              col                  (m/find-first #(= (:id %) (meta/id :venues :name)) breakoutable-columns)
              query'               (lib/breakout query col)]
          (is (=? {:lib/type :mbql/query
                   :database (meta/id)
                   :stages   [{:lib/type     :mbql.stage/mbql
                               :source-table (meta/id :venues)
                               :lib/options  {:lib/uuid string?}
                               :breakout     [[:field {:lib/uuid string? :base-type :type/Text} (meta/id :venues :name)]]}]}
                  query'))
          (is (=? [[:field {:lib/uuid string? :base-type :type/Text} (meta/id :venues :name)]]
                  (lib/breakouts query')))
          (is (true? (breakout-column-excluded? query col query'))))))))

(deftest ^:parallel breakoutable-columns-own-and-implicitly-joinable-columns-e2e-test
  (testing "An implicitly joinable column can be broken out by."
    (let [query (lib/query-for-table-name meta/metadata-provider "VENUES")
          cat-name-col (m/find-first #(= (:id %) (meta/id :categories :name))
                                     (lib/breakoutable-columns query))
          ven-price-col (m/find-first #(= (:id %) (meta/id :venues :price))
                                      (lib/breakoutable-columns query))
          query' (-> query
                     (lib/breakout cat-name-col)
                     (lib/breakout ven-price-col))]
      (is (=? {:stages [{:breakout [[:field
                                     {:source-field (meta/id :venues :category-id)}
                                     (meta/id :categories :name)]
                                    [:field
                                     {:lib/uuid string? :base-type :type/Integer}
                                     (meta/id :venues :price)]]}]}
              query'))
      (is (= "Venues, Grouped by Categories → Name and Price"
             (lib/describe-query query')))
      (is (=? [{:display_name "ID",          :lib/source :source/table-defaults}
               {:display_name "Name",        :lib/source :source/table-defaults}
               {:display_name "Category ID", :lib/source :source/table-defaults}
               {:display_name "Latitude",    :lib/source :source/table-defaults}
               {:display_name "Longitude",   :lib/source :source/table-defaults}
               {:display_name "ID",          :lib/source :source/implicitly-joinable}]
              (lib/breakoutable-columns query'))))))

(deftest ^:parallel breakoutable-columns-with-source-card-e2e-test
  (testing "A column that comes from a source Card (Saved Question/Model/etc) can be broken out by."
    (let [query (lib.tu/query-with-card-source-table)]
      (testing (lib.util/format "Query =\n%s" (u/pprint-to-str query))
        (let [name-col (m/find-first #(= (:name %) "USER_ID")
                                     (lib/breakoutable-columns query))]
          (is (=? {:name      "USER_ID"
                   :base_type :type/Integer}
                  name-col))
          (let [query' (lib/breakout query name-col)]
            (is (=? {:stages
                     [{:source-table "card__1"
                       :breakout [[:field {:base-type :type/Integer} "USER_ID"]]}]}
                    query'))
            (is (= "My Card, Grouped by User ID"
                   (lib/describe-query query')))
            (is (= ["User ID"]
                   (for [breakout (lib/breakouts query')]
                     (lib/display-name query' breakout))))
          (is (true? (breakout-column-excluded? query name-col query')))))))))

(deftest ^:parallel breakoutable-columns-expression-e2e-test
  (let [query (-> (lib/query-for-table-name meta/metadata-provider "VENUES")
                  (lib/expression "expr" (lib/absolute-datetime "2020" :month))
                  (lib/with-fields [(lib/field "VENUES" "ID")]))]
    (is (=? [{:id (meta/id :venues :id),          :name "ID",          :display_name "ID",          :lib/source :source/table-defaults}
             {:id (meta/id :venues :name),        :name "NAME",        :display_name "Name",        :lib/source :source/table-defaults}
             {:id (meta/id :venues :category-id), :name "CATEGORY_ID", :display_name "Category ID", :lib/source :source/table-defaults}
             {:id (meta/id :venues :latitude),    :name "LATITUDE",    :display_name "Latitude",    :lib/source :source/table-defaults}
             {:id (meta/id :venues :longitude),   :name "LONGITUDE",   :display_name "Longitude",   :lib/source :source/table-defaults}
             {:id (meta/id :venues :price),       :name "PRICE",       :display_name "Price",       :lib/source :source/table-defaults}
             {:name "expr", :display_name "expr", :lib/source :source/expressions}
             {:id (meta/id :categories :id),   :name "ID",   :display_name "ID",   :lib/source :source/implicitly-joinable}
             {:id (meta/id :categories :name), :name "NAME", :display_name "Name", :lib/source :source/implicitly-joinable}]
            (lib/breakoutable-columns query)))
    (let [expr (m/find-first #(= (:name %) "expr") (lib/breakoutable-columns query))]
      (is (=? {:lib/type   :metadata/field
               :lib/source :source/expressions
               :name       "expr"}
              expr))
      (let [query' (lib/breakout query expr)]
        (is (=? {:stages [{:breakout [[:expression {} "expr"]]}]}
                query'))
        (testing "description"
          (is (= "Venues, Grouped by expr"
                 (lib/describe-query query'))))
        (is (true? (breakout-column-excluded? query expr query')))))))

(deftest ^:parallel breakoutable-columns-new-stage-e2e-test
  (let [query (-> (lib/query-for-table-name meta/metadata-provider "VENUES")
                  (lib/expression "expr" (lib/absolute-datetime "2020" :month))
                  (lib/with-fields [(lib/field "VENUES" "ID")
                                    (lib.dev/expression-ref "expr")])
                  (lib/append-stage))]
    (is (=? [{:id (meta/id :venues :id), :name "ID", :display_name "ID", :lib/source :source/previous-stage}
             {:name "expr", :display_name "expr", :lib/source :source/previous-stage}]
            (lib/breakoutable-columns query)))
    (let [expr (m/find-first #(= (:name %) "expr") (lib/breakoutable-columns query))]
      (is (=? {:lib/type   :metadata/field
               :lib/source :source/previous-stage
               :name       "expr"}
              expr))
      (let [query' (lib/breakout query expr)]
        (is (=? {:stages [{:lib/type :mbql.stage/mbql, :source-table (meta/id :venues)}
                          {:breakout [[:field {:base-type :type/Date, :effective-type :type/Date} "expr"]]}]}
                query'))
        (testing "description"
          (is (= "Grouped by Expr"
                 (lib/describe-query query'))))
        (is (true? (breakout-column-excluded? query expr query')))))))
