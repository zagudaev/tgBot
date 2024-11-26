package org.example.model;

import lombok.Data;
import lombok.NoArgsConstructor;


import java.io.File;
import java.util.List;

@Data
@NoArgsConstructor
public class Post {
    private String theme;
    private String title;
    private String text;
    private String author;
    private List<File> image;

    public Post(String theme) {
        this.theme = theme;
    }
}
