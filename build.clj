(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'io.metaref/async)
(def version "0.0.1")
#_ ; alternatively, use MAJOR.MINOR.COMMITS:
(def version (format "0.1.%s" (b/git-count-revs nil)))
(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean "Clean target folder" [_]
  (b/delete {:path "target"}))

(defn compile-java [_]
  (clean nil)
  (b/javac {:src-dirs ["src/java"]
            :class-dir class-dir
            :basis @basis
            :javac-opts ["--release" "21" "-proc:full"]}))

(defn test "Run all the tests." [opts]
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command
                  {:basis      basis
                    :main      'clojure.main
                    :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn- jar-opts [opts]
  (assoc opts
          :lib lib :version version
          :jar-file (format "target/%s-%s.jar" lib version)
          :scm {:tag (str "v" version)}
          :basis @basis
          :class-dir class-dir
          :target "target"
          :src-dirs ["src/clojure"]))

(defn jar "Build the JAR." [opts] 
  (let [opts (jar-opts opts)]
    (println "\nCompiling...")
    (compile-java nil)
    (println "\nWriting pom.xml...")
    (b/write-pom opts)
    (println "\nCopying source...")
    (b/copy-dir {:src-dirs ["resources" "src/clojure"] :target-dir class-dir})
    (println "\nBuilding JAR...")
    (b/jar opts))
  opts)

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (println "\nRunning tests...")
  (test opts)
  (jar opts))

(defn install "Install the JAR locally." [opts]
  (let [opts (jar-opts opts)]
    (b/install opts))
  opts)

(defn deploy "Deploy the JAR to Clojars." [opts]
  (let [{:keys [jar-file] :as opts} (jar-opts opts)]
    (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
                :pom-file (b/pom-path (select-keys opts [:lib :class-dir]))}))
  opts)
