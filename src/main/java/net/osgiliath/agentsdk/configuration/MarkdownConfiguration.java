package net.osgiliath.agentsdk.configuration;

import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.parser.Parser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MarkdownConfiguration {

    @Bean
    public Parser markdownParser() {
        return Parser.builder()
            .extensions(List.of(
                YamlFrontMatterExtension.create(),
                TablesExtension.create(),
                TaskListItemsExtension.create(),
                AutolinkExtension.create(),
                InsExtension.create()
            ))
            .build();
    }
}
