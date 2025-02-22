(ns metabase.lib.types.constants
  "Ported from frontend/src/metabase-lib/types/constants.js"
  #?(:cljs (:require [goog.object :as gobj])))

#?(:cljs
   (do
     (def ^:export name->type
       "A map of Type name (as string, without `:type/` namespace) -> type keyword

         {\"Temporal\" :type/Temporal, ...}"
       (reduce (fn [m typ] (doto m (gobj/set (name typ) typ)))
               #js {}
               (distinct (mapcat descendants [:type/* :Semantic/* :Relation/*]))))

     ;; primary field types used for picking operators, etc
     (def ^:export key-number "JS-friendly access for the number type" ::number)
     (def ^:export key-string "JS-friendly access for the string type" ::string)
     (def ^:export key-string-like "JS-friendly access for the string-like type" ::string-like)
     (def ^:export key-boolean "JS-friendly access for the boolean type" ::boolean)
     (def ^:export key-temporal "JS-friendly access for the temporal type" ::temporal)
     (def ^:export key-location "JS-friendly access for the location type" ::location)
     (def ^:export key-coordinate "JS-friendly access for the coordinate type" ::coordinate)
     (def ^:export key-foreign-KEY "JS-friendly access for the foreign-key type" ::foreign-key)
     (def ^:export key-primary-KEY "JS-friendly access for the primary-key type" ::primary-key)

     ;; other types used for various purposes
     (def ^:export key-summable "JS-friendly access for the summable type" ::summable)
     (def ^:export key-scope "JS-friendly access for the scope type" ::scope)
     (def ^:export key-category "JS-friendly access for the category type" ::category)

     (def ^:export key-unknown "JS-friendly access for the unknown type" ::unknown)))

;; NOTE: be sure not to create cycles using the "other" types
(def type-hierarchies
  "A front-end specific type hierarchy used by [[metabase.lib.types.isa/field-type?]].
  It is not meant to be used directly."
  {::temporal    {:effective_type [:type/Temporal]
                  :semantic_type  [:type/Temporal]}
   ::number      {:effective_type [:type/Number]
                  :semantic_type  [:type/Number]}
   ::string      {:effective_type [:type/Text]
                  :semantic_type  [:type/Text :type/Category]}
   ::string_like {:effective_type [:type/TextLike]}
   ::boolean     {:effective_type [:type/Boolean]}
   ::coordinate  {:semantic_type [:type/Coordinate]}
   ::location    {:semantic_type [:type/Address]}
   ::entity      {:semantic_type [:type/FK :type/PK :type/Name]}
   ::foreign_key {:semantic_type [:type/FK]}
   ::primary_key {:semantic_type [:type/PK]}
   ::summable    {:include [::number]
                  :exclude [::entity ::location ::temporal]}
   ::scope       {:include [::number ::temporal ::category ::entity ::string]
                  :exclude [::location]}
   ::category    {:effective_type [:type/Boolean]
                  :semantic_type  [:type/Category]
                  :include        [::location]}
   ;; NOTE: this is defunct right now.  see definition of metabase.lib.types.isa/dimension?.
   ::dimension   {:include [::temporal ::category ::entity]}})
