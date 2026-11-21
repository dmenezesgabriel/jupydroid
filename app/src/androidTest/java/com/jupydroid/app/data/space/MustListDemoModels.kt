package com.jupydroid.app.data.space

/** Plotting through SHAP: the visualisation and classical-ML layer. */
object MustListDemoModels {
  val PACKAGES: List<MustListPackage> = listOf(
    MustListPackage(
      "matplotlib",
      """
      import matplotlib
      matplotlib.use("Agg")
      import matplotlib.pyplot as plt
      plt.plot([1, 2, 3, 4])
      plt.ylabel("some numbers")
      plt.savefig("plot.png")
      import os
      print("saved", os.path.getsize("plot.png"))
      """.trimIndent(),
      Regex("saved [0-9]+"),
    ),
    MustListPackage(
      "seaborn",
      """
      import matplotlib
      matplotlib.use("Agg")
      import matplotlib.pyplot as plt
      import pandas as pd
      import seaborn as sns
      sns.set_theme()
      tips = pd.DataFrame({"total_bill": [10, 20, 15, 25], "tip": [1, 3, 2, 4],
                           "day": ["Sun", "Sat", "Sun", "Thur"]})
      sns.scatterplot(data=tips, x="total_bill", y="tip", hue="day")
      plt.savefig("seaborn.png")
      import os
      print("saved", os.path.getsize("seaborn.png"))
      """.trimIndent(),
      Regex("saved [0-9]+"),
    ),
    MustListPackage(
      "plotly",
      """
      import plotly.express as px
      df = px.data.gapminder().query("year==2007")
      fig = px.scatter(df, x="gdpPercap", y="lifeExp", size="pop", color="continent",
                       hover_name="country", log_x=True, size_max=60)
      print(fig.data[0].type, sum(len(t.x) for t in fig.data))
      """.trimIndent(),
      Regex("scatter 142"),
    ),
    MustListPackage(
      "scikit-learn",
      """
      from sklearn.datasets import load_iris
      from sklearn.ensemble import RandomForestClassifier
      from sklearn.model_selection import train_test_split
      X, y = load_iris(return_X_y=True)
      X_train, X_test, y_train, y_test = train_test_split(X, y, random_state=0)
      clf = RandomForestClassifier(random_state=0).fit(X_train, y_train)
      print(clf.score(X_test, y_test))
      """.trimIndent(),
      Regex("0\\.[0-9]+"),
    ),
    MustListPackage(
      "xgboost",
      """
      import numpy as np
      import xgboost as xgb
      rng = np.random.default_rng(0)
      X = rng.random((100, 4))
      y = (X[:, 0] > 0.5).astype(int)
      dtrain = xgb.DMatrix(X, label=y)
      bst = xgb.train({"objective": "binary:logistic"}, dtrain, num_boost_round=10)
      print(bst.predict(dtrain)[:5])
      """.trimIndent(),
      Regex("\\[0\\.[0-9]+"),
    ),
    MustListPackage(
      "lightgbm",
      """
      import numpy as np
      import lightgbm as lgb
      rng = np.random.default_rng(0)
      X = rng.random((100, 5))
      y = rng.integers(0, 2, 100)
      train_data = lgb.Dataset(X, label=y)
      bst = lgb.train({"objective": "binary", "verbosity": -1}, train_data, num_boost_round=10)
      print(bst.predict(X)[:5])
      """.trimIndent(),
      Regex("\\[0\\.[0-9]+"),
    ),
    MustListPackage(
      "catboost",
      """
      import numpy as np
      from catboost import CatBoostClassifier
      rng = np.random.default_rng(0)
      X = rng.random((50, 4))
      y = rng.integers(0, 2, 50)
      model = CatBoostClassifier(iterations=10, verbose=0)
      model.fit(X, y)
      print(model.predict(X[:3]))
      """.trimIndent(),
      Regex("\\[0|\\[1"),
    ),
    MustListPackage(
      "imbalanced-learn",
      """
      from sklearn.datasets import make_classification
      from imblearn.over_sampling import SMOTE
      X, y = make_classification(n_samples=1000, n_features=2, n_redundant=0,
                                 n_clusters_per_class=1, weights=[0.1, 0.9], random_state=42)
      X_res, y_res = SMOTE(random_state=42).fit_resample(X, y)
      print(len(set(y_res)), X_res.shape[0] > X.shape[0])
      """.trimIndent(),
      Regex("2 True"),
    ),
    MustListPackage(
      "statsmodels",
      """
      import numpy as np
      import statsmodels.api as sm
      rng = np.random.default_rng(0)
      X = sm.add_constant(rng.standard_normal((100, 2)))
      y = X @ np.array([1.0, 2.0, 3.0]) + rng.standard_normal(100) * 0.1
      model = sm.OLS(y, X).fit()
      print(model.rsquared)
      """.trimIndent(),
      Regex("0\\.9"),
    ),
    MustListPackage(
      "shap",
      """
      import numpy as np
      import shap
      from sklearn.ensemble import RandomForestRegressor
      rng = np.random.default_rng(0)
      X = rng.random((100, 4))
      y = X[:, 0] * 2 + rng.random(100) * 0.1
      model = RandomForestRegressor(n_estimators=10, random_state=0).fit(X, y)
      explainer = shap.TreeExplainer(model)
      print(np.abs(explainer.shap_values(X[:5])).mean())
      """.trimIndent(),
      Regex("[0-9]\\.[0-9]+"),
    ),
  )
}
