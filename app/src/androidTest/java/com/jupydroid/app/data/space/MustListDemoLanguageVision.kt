package com.jupydroid.app.data.space

/** Datasets through OpenCV: the language-model and image layer. */
object MustListDemoLanguageVision {
  val PACKAGES: List<MustListPackage> = listOf(
    MustListPackage(
      "datasets",
      """
      from datasets import load_dataset
      ds = load_dataset("lhoestq/demo1")
      print(ds)
      """.trimIndent(),
      Regex("DatasetDict"),
    ),
    MustListPackage(
      "spacy",
      """
      import spacy
      nlp = spacy.blank("en")
      doc = nlp("This is a sentence.")
      print([(token.text, token.pos_) for token in doc])
      """.trimIndent(),
      Regex("\\('This', ''\\)"),
    ),
    MustListPackage(
      "nltk",
      """
      import nltk
      nltk.download("punkt_tab", quiet=True)
      from nltk.tokenize import word_tokenize
      print(word_tokenize("This is a test."))
      """.trimIndent(),
      Regex("\\['This', 'is', 'a', 'test', '\\.'\\]"),
    ),
    MustListPackage(
      "opencv-python",
      """
      import cv2
      import numpy as np
      img = np.zeros((100, 100, 3), dtype=np.uint8)
      cv2.imwrite("blank.png", img)
      img2 = cv2.imread("blank.png")
      print(img2.shape)
      """.trimIndent(),
      Regex("\\(100, 100, 3\\)"),
    ),
  )
}
