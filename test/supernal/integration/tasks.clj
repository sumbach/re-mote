(ns supernal.integration.tasks
  "Test basic dsl usage https://github.com/narkisr/puppet-supernal" 
  (:use 
     midje.sweet
     supernal.integration.common
    [supernal.baseline :only (base-deploy)]
    [taoensso.timbre :only (warn debug)]
    [supernal.core :only (ns- execute execute-task run copy env lifecycle)]))



(env 
  {:roles {
      :web #{{:host (base-net ".26") :user "vagrant" :sudo true}}}
   })

(ns- deploy 
  (task stop
     (debug "here")
     (debug "stopping service" remote)
     (run "hostname")))

(ns- error
    (task zero-div
      (/ 1 0)))

(lifecycle includes-error {:doc "Includes an error"} {deploy/stop #{error/zero-div} })

(def artifact "git://github.com/narkisr/swag.git")

(defn extract-error [{:keys [fail]}]
  {:fail (-> fail bean :message)})

(def ok [{:ok nil :remote  {:host (base-net ".26") :sudo true :user "vagrant"}}])

(fact "base deployment tasks no join" :integration :supernal
   (execute base-deploy {:app-name "foo" :src artifact} :web) => ok)

(fact "single task" :integration :supernal
   (execute-task deploy/stop {:app-name "foo" :src artifact} :web) => ok)

(fact "env option" :integration :supernal
   (let [e {:roles {:web #{{:host (base-net ".26") :user "vagrant" :sudo true}}} }]
     (execute-task deploy/stop {:app-name "foo" :src artifact} :web :env e) => ok))

(fact "task with error" :integration :supernal
  (map extract-error (execute-task error/zero-div {:app-name "foo" :src artifact} :web)) => [{:fail "Divide by zero"}])

(fact "lifecycle with error" :integration :supernal
   (map extract-error (execute includes-error {:app-name "foo" :src artifact} :web)) => 
   '({:fail "Failed to run #'supernal.user.error/zero-div due to java.lang.ArithmeticException: Divide by zero"})   
   
   )
