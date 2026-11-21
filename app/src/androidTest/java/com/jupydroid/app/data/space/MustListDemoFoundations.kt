package com.jupydroid.app.data.space

/** NumPy through Pillow: the array and dataframe layer every other entry stands on. */
object MustListDemoFoundations {
  val PACKAGES: List<MustListPackage> = listOf(
    MustListPackage(
      "numpy",
      """
      import numpy as np
      a = np.arange(6).reshape(2, 3)
      print(a.mean(), a.sum())
      """.trimIndent(),
      Regex("2\\.5 15"),
    ),
    MustListPackage(
      "pandas",
      """
      import pandas as pd
      df = pd.DataFrame({"name": ["alice", "bob"], "score": [10, 20]})
      print(df)
      """.trimIndent(),
      Regex("score"),
    ),
    MustListPackage(
      "scipy",
      """
      import numpy as np
      from scipy.linalg import solve
      a = np.array([[3.0, 2.0, 0.0], [1.0, -1.0, 0.0], [0.0, 5.0, 4.0]])
      b = np.array([1.0, 2.0, 3.0])
      print(" ".join(f"{v:.1f}" for v in solve(a, b)))
      """.trimIndent(),
      Regex("1\\.0 -1\\.0 2\\.0"),
    ),
    MustListPackage(
      "polars",
      """
      import polars as pl
      df = pl.DataFrame({"foo": [1, 2, 3], "bar": [4, 5, 6]})
      print(df)
      """.trimIndent(),
      Regex("shape: \\(3, 2\\)"),
    ),
    MustListPackage(
      "pyarrow",
      """
      import pyarrow as pa
      table = pa.table({"a": [1, 2, 3], "b": ["x", "y", "z"]})
      print(table.num_rows, table.column_names)
      """.trimIndent(),
      Regex("3 \\['a', 'b'\\]"),
    ),
    MustListPackage(
      "pillow",
      """
      from PIL import Image, ImageDraw
      img = Image.new("RGB", (100, 100), "white")
      ImageDraw.Draw(img).text((10, 10), "hello")
      img.save("hello.png")
      print(Image.open("hello.png").size)
      """.trimIndent(),
      Regex("\\(100, 100\\)"),
    ),
  )
}
