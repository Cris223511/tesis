library(treemapify)
library(ggplot2)
library(dplyr)

df_no_ens <- data.frame(
  Modelo = c("Wearable/Mobile", "Traditional ML", "CNN/Deep Learning",
             "Specialized Apps", "Other Models", "Eye-tracking/CV"),
  Frecuencia = c(10, 8, 5, 5, 5, 4)
)

df_no_ens <- df_no_ens %>% arrange(desc(Frecuencia))
df_no_ens$Porcentaje <- round(df_no_ens$Frecuencia / sum(df_no_ens$Frecuencia) * 100, 1)

colores_no_ens <- c("#4472c4", "#70ad47", "#a5a5a5", "#ffc000", "#ed7d31", "#5b9bd5")

ggplot(df_no_ens, aes(area = Frecuencia, fill = Modelo, 
                      label = paste0(Modelo, "\n", Porcentaje, "%"))) +
  geom_treemap() +
  geom_treemap_text(colour = "black",
                    place = "centre", 
                    size = 18, 
                    grow = FALSE,
                    fontface = "bold") +
  scale_fill_manual(values = colores_no_ens) +
  theme(legend.position = "none")

ggsave("figura2_no_ensamblados.png", width = 14, height = 10, dpi = 300)