package com.example.jpalab.dto;

import lombok.Getter;

@Getter
public class AuthorBookCountDto {

    private final Long authorId;
    private final String authorName;
    private final Long bookCount;

    public AuthorBookCountDto(Long authorId, String authorName, Long bookCount) {
        this.authorId = authorId;
        this.authorName = authorName;
        this.bookCount = bookCount;
    }
}
