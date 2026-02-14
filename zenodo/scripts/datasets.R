library(treemap)
library(dplyr)

public_datasets <- data.frame(
  Dataset = c("CAFE", "FER-2013", "CAFE+GuessWhat", "Q-Chat-10", 
              "FEI/GT/Gallagher", "STREs WoZ", "GuessWhat App"),
  Frequency = c(3, 2, 1, 1, 1, 1, 1),
  Category = "PUBLIC (23.8%)"
)

private_datasets <- data.frame(
  Dataset = c("MoodCapture", "Mobile Landmarks", "D-MOMO", 
              "App Reviews", "Children Drawings", "WEMAC", 
              "YouTube Videos", "Children Art", "Other Private"),
  Frequency = c(1, 1, 1, 1, 2, 1, 1, 1, 7),
  Category = "PRIVATE (38.1%)"
)

df_datasets <- rbind(public_datasets, private_datasets)

total_studies <- 42
df_datasets$Percentage <- round(df_datasets$Frequency / total_studies * 100, 1)
df_datasets$Label <- paste0(df_datasets$Dataset, "\n", df_datasets$Percentage, "%")

treemap(df_datasets,
        index = c("Category", "Label"),
        vSize = "Frequency",
        vColor = "Category",
        type = "categorical",
        palette = c("#4169E1", "#FFD700"),
        fontsize.labels = c(0, 13),
        fontcolor.labels = c("transparent", "black"),
        fontface.labels = c(1, 2),
        align.labels = list(c("center", "center"), c("center", "center")),
        overlap.labels = 0.5,
        inflate.labels = FALSE,
        border.col = "black",
        border.lwds = 2)
