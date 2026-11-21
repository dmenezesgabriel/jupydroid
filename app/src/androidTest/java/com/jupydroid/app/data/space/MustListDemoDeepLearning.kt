package com.jupydroid.app.data.space

/**
 * PyTorch through JAX: the framework layer, each in a kernel to itself - 3.5 GB of RAM cannot
 * hold two. Two lists, not one: torch's aarch64 wheel pulls the whole NVIDIA CUDA stack, ~7 GB
 * extracted, so it gets a root to itself and the TensorFlow pair theirs - keras needs the
 * tensorflow backend, so they cannot be split from each other. transformers joins the
 * torch root instead: its pipeline needs a deep-learning backend and transformers 5.x
 * dropped tensorflow's, leaving torch or jax - both already here.
 */
object MustListDemoDeepLearning {
  val PACKAGES: List<MustListPackage> = listOf(
    MustListPackage(
      "torch",
      """
      import torch
      x = torch.rand(5, 3)
      y = torch.rand(3, 5)
      print(x.matmul(y).shape)
      """.trimIndent(),
      Regex("torch\\.Size\\(\\[5, 5\\]\\)"),
    ),
    MustListPackage(
      "torchvision",
      """
      import torch
      from torchvision import models
      model = models.resnet18(weights=None)
      print(sum(p.numel() for p in model.parameters()))
      """.trimIndent(),
      Regex("[0-9]{7,}"),
    ),
    MustListPackage(
      "jax",
      """
      import jax
      import jax.numpy as jnp
      def f(x):
          return jnp.sum(jnp.sin(x))
      print(jax.grad(f)(jnp.array([1.0, 2.0, 3.0])))
      """.trimIndent(),
      Regex("\\[\\s*-?[0-9]\\.[0-9]+"),
    ),
    MustListPackage(
      "transformers",
      """
      from transformers import pipeline
      classifier = pipeline("sentiment-analysis")
      print(classifier("I love you")[0])
      """.trimIndent(),
      Regex("POSITIVE|NEGATIVE"),
    ),
  )

  /** tensorflow then keras: keras' default backend is tensorflow, so the pair shares a root. */
  val FRAMEWORKS: List<MustListPackage> = listOf(
    MustListPackage(
      "tensorflow",
      """
      import tensorflow as tf
      a = tf.constant([[1, 2], [3, 4]])
      print(a.shape, int(tf.reduce_sum(a)))
      """.trimIndent(),
      Regex("\\(2, 2\\) 10"),
    ),
    MustListPackage(
      "keras",
      """
      import keras
      from keras import layers
      inputs = keras.Input(shape=(784,))
      x = layers.Dense(32, activation="relu")(inputs)
      outputs = layers.Dense(10, activation="softmax")(x)
      model = keras.Model(inputs=inputs, outputs=outputs)
      model.compile(optimizer="adam", loss="sparse_categorical_crossentropy")
      print(model.count_params())
      """.trimIndent(),
      Regex("[0-9]{4,}"),
    ),
  )
}
