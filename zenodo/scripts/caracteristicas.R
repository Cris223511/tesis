library(ggplot2)
library(dplyr)

df_features <- data.frame(
  Features_Group = c("Facial Features", "CNN Architectures", 
                     "Input Modalities", "Optimization Methods",
                     "Physiological Signals", "Textual/Audio Features"),
  Count = c(17, 10, 6, 4, 3, 2),
  Percentage = c(40.48, 23.81, 14.29, 9.52, 7.14, 4.76)
)

df_features <- df_features %>% arrange(desc(Count))

colores_features <- c("#4472c4", "#70ad47", "#a5a5a5", 
                      "#ffc000", "#ed7d31", "#5b9bd5")

ggplot(df_features, aes(x = reorder(Features_Group, Count), 
                        y = Count, fill = Features_Group)) +
  geom_bar(stat = "identity", width = 0.7) +
  geom_text(aes(label = paste0(Count, " (", Percentage, "%)")), 
            hjust = -0.1, size = 4.5, fontface = "bold") +
  coord_flip() +
  scale_fill_manual(values = colores_features) +
  labs(x = "", y = "Frequency of Use") +
  theme_minimal() +
  theme(legend.position = "none",
        axis.text.y = element_text(size = 13, face = "bold", color = "black"),
        axis.text.x = element_text(size = 13, color = "black"),
        axis.title.x = element_text(size = 13, face = "bold", color = "black"),
        panel.grid.major = element_line(color = "gray90"),
        panel.grid.minor = element_blank()) +
  ylim(0, max(df_features$Count) * 1.15)

ggsave("figure8_feature_groups.png", width = 12, height = 8, dpi = 300)