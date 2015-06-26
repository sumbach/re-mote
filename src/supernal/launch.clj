(comment 
   Celestial, Copyright 2012 Ronen Narkis, narkisr.com
   Licensed under the Apache License,
   Version 2.0  (the "License") you may not use this file except in compliance with the License.
   You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.)

(ns supernal.launch
  (:refer-clojure :exclude  [list])
  (:require  
    [taoensso.timbre :refer (warn debug)]
    [clojure.walk :refer (keywordize-keys)]
    [clojure.string :refer (split)]
    [clojure.core.strint :refer (<<)]  
    [clansi.core :refer (style)]
    [cliopatra.command :as command :refer  [defcommand]])
  (:gen-class true))

(defn list-tasks []
  (map (juxt identity (comp ns-publics symbol))
     (filter #(.startsWith % "supernal.user") (map #(-> % ns-name str) (all-ns)))))

(defn clear-prefix [ns-]
 (.replace ns- "supernal.user." ""))

(defn readable-form [[ns- ts]]
  (reduce (fn [r name*] (conj r (<< "~(clear-prefix ns-)/~{name*}" ))) #{} (keys ts)))

(defn task-exists? [full-name]
  (seq (filter #(% full-name) (map readable-form (into {} (list-tasks))))))

(defn get-cycles [] (deref (var-get (find-var 'supernal.core/cycles))))

(defn lifecycle-exists? [name*]
  ((into #{} (map #(-> % meta :name str ) (get-cycles))) name*))

(defmacro adhoc-eval [e]
   `(binding [*ns* (find-ns 'supernal.adhoc)] (eval ~e)))

(defn shout! [output]
   (println (style output :red))
   (System/exit 1))

(defn split-args 
  [args]
  {:pre [(= (first (re-find #"((\w|\-)+\=[^\s]+,?)*" args)) args)]}
  (keywordize-keys (into {} (map #(into [] (split % #"=")) (split args #"," )))))

(defn summarize
  [rs]
  (let [success (filter #(contains? % :ok) rs) fail (filter #(contains? % :fail) rs)]
    (println (style "Run summary:" :blue) "\n") 
    (doseq [r success]
      (println " " (style "✔" :green) (get-in r [:remote :host])))
    (doseq [r fail]
      (println " " (style "x" :red) (get-in r [:remote :host]) "-" (.getMessage (r :fail))))
    (println (<< "\nTotal: ~(count success) successful, ~(count fail) failed!")))
  )

(defcommand run 
  "run a single task or an entire lifecycle

  sup run {task/lifecycle} -s {script} -r {role} -a src=\"{uri}\",app-name=\"{name}\"\"

  * standalone tasks should be prefixed (deploy/start)."
  {:opts-spec [["-s" "--script" "Script to run" :default "deploy.clj"]
               ["-r" "--role" "Target Role" :required true]
               ["-a" "--args" "Task/Cycle arguments src=\"uri\" app-name=\"name\"" :default ""]]
   :bind-args-to [name*]}
  (load-string (slurp script))
  (let [args* (if (empty? args) {} (split-args args))]
    (if (lifecycle-exists? name*)
      (summarize (adhoc-eval (clojure.core/list 'execute (symbol name*) args* (keyword role) :join true)))
      (if (task-exists? name*) 
        (adhoc-eval (clojure.core/list 'execute-task (symbol name*) args* (keyword role) :join true)) 
        (shout! (<< "No matching lifecycle or task named ~{name*} found!"))))))

(defn print-tasks []
  (println (style "Tasks:" :blue))
  (doseq [[n ts] (list-tasks)]
    (println " " (style (<< "~(clear-prefix n):") :yellow))
    (doseq [[name* fn*] ts]
      (println "  " (style name* :green) (<< "- ~(:desc (meta (var-get fn*)))")))))

(defn print-cycles []
  (println (style "Lifecycles:" :blue))
  (doseq [c (get-cycles)]
    (let [{:keys [name]} (meta c) {:keys [doc]} (meta (var-get c))]
      (println " "  (style name :green) (<< "- ~{doc}")))))

(defcommand list
  "lists available tasks and lifecycles:

  sup list
  "
  {:opts-spec [["-s" "--script" "Script to run" :default "deploy.clj"]]}
  (load-string (slurp script))
  (print-cycles) 
  (print-tasks))

(defcommand version 
  "list supernal version and info:

  sup version 
  " 
  {:opts-spec [] :bind-args-to [script]}
  (println "Supernal 0.5.3"))

(defn formatting [{:keys [level ?err_ vargs_ msg_ ?ns-str hostname_ timestamp_]}]
  (let [date (force timestamp_)]
    (format "%s %s\n" date (or (force msg_) ""))))

(defn slf4j-fix []
  (let [cl (.getContextClassLoader  (Thread/currentThread))]
    (-> cl  
        (.loadClass "org.slf4j.LoggerFactory")
        (.getMethod "getLogger"  (into-array java.lang.Class [(.loadClass cl "java.lang.String")]))
        (.invoke nil (into-array java.lang.Object ["ROOT"])))))


(slf4j-fix)

(defn -main [& args]
  (binding [*ns* (create-ns 'supernal.adhoc)] 
    (use '[clojure.core])
    (use '[supernal.core :only (ns- execute execute-task run copy env cycles lifecycle)])
    (use '[supernal.baseline])
    (use '[taoensso.timbre :only (warn debug info error)])
    (taoensso.timbre/merge-config! {:output-fn  formatting })
    (taoensso.timbre/merge-config! {:timestamp-opts  {:pattern "dd/MM/YY HH:MM:ss"}})
    (taoensso.timbre/set-level! :trace)
    (command/dispatch 'supernal.launch args)))


(comment 
  (-main "run" "fixtures/supernal-demo.clj" "base-deploy" "-r" "web") 
  )

