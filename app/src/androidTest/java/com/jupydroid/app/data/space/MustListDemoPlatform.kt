package com.jupydroid.app.data.space

/** Optuna through Airflow: tuning, tracking, big-data and serving. */
object MustListDemoPlatform {
  val PACKAGES: List<MustListPackage> = listOf(
    MustListPackage(
      "optuna",
      """
      import optuna
      def objective(trial):
          x = trial.suggest_float("x", -10, 10)
          return (x - 2) ** 2
      study = optuna.create_study()
      study.optimize(objective, n_trials=20)
      print(study.best_params)
      """.trimIndent(),
      Regex("'x':"),
    ),
    MustListPackage(
      "mlflow",
      """
      import mlflow
      mlflow.set_tracking_uri("sqlite:///mlruns.db")
      with mlflow.start_run():
          mlflow.log_param("alpha", 0.5)
          mlflow.log_metric("accuracy", 0.9)
          print("run:", mlflow.active_run().info.run_id)
      """.trimIndent(),
      Regex("run: [0-9a-f]{32}"),
    ),
    MustListPackage(
      "pyspark",
      """
      from pyspark.sql import SparkSession
      spark = SparkSession.builder.appName("demo").getOrCreate()
      df = spark.createDataFrame([(1, "a"), (2, "b")], ["id", "val"])
      print(df.count())
      """.trimIndent(),
      Regex("2"),
      // PyPI publishes pyspark as an sdist only, and the Space's pip is wheels-only
      // (PIP_ONLY_BINARY): there is no released wheel for pip to find anywhere.
      limitation = Regex("No matching distribution found for pyspark"),
    ),
    MustListPackage(
      "dask",
      """
      import dask.array as da
      x = da.ones((1000, 1000), chunks=(100, 100))
      print(float(x.sum().compute()))
      """.trimIndent(),
      Regex("1000000"),
    ),
    MustListPackage(
      "ray",
      """
      import ray
      ray.init(include_dashboard=False, ignore_reinit_error=True)
      @ray.remote
      def hello():
          return "Hello from Ray"
      print(ray.get(hello.remote()))
      """.trimIndent(),
      Regex("Hello from Ray"),
      // The wheel installs, but ray.init's runtime cannot start under proot - measured
      // on-device: raylet comes up and the driver reports AccessDenied.
      limitation = Regex("AccessDenied: \\(pid="),
    ),
    MustListPackage(
      "jupyter",
      """
      import nbformat
      import notebook
      import jupyter_server
      print(nbformat.__version__, notebook.__version__)
      """.trimIndent(),
      Regex("[0-9]+\\.[0-9]+\\.[0-9]+ [0-9]+\\.[0-9]+\\.[0-9]+"),
    ),
    MustListPackage(
      "ipython",
      """
      import IPython
      print(IPython.__version__)
      """.trimIndent(),
      Regex("[0-9]+\\.[0-9]+\\.[0-9]+"),
    ),
    MustListPackage(
      "fastapi",
      """
      from fastapi import FastAPI
      app = FastAPI()
      @app.get("/")
      def read_root():
          return {"Hello": "World"}
      @app.get("/items/{item_id}")
      def read_item(item_id: int, q: str = None):
          return {"item_id": item_id, "q": q}
      print([r.path for r in app.routes])
      """.trimIndent(),
      Regex("/items/\\{item_id\\}"),
    ),
    MustListPackage(
      "sqlalchemy",
      """
      from sqlalchemy import Column, Integer, String, create_engine
      from sqlalchemy.orm import Session, declarative_base
      engine = create_engine("sqlite:///demo.db")
      Base = declarative_base()
      class User(Base):
          __tablename__ = "user"
          id = Column(Integer, primary_key=True)
          name = Column(String)
      Base.metadata.create_all(engine)
      with Session(engine) as s:
          s.add(User(name="alice"))
          s.commit()
          print(s.query(User.name).all())
      """.trimIndent(),
      Regex("\\('alice',\\)"),
    ),
    MustListPackage(
      "apache-airflow",
      """
      import airflow
      print(airflow.__version__)
      """.trimIndent(),
      Regex("[0-9]+\\.[0-9]+\\.[0-9]+"),
    ),
  )
}
