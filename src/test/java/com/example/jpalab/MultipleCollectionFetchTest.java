package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.jpalab.domain.Author;
import com.example.jpalab.domain.Award;
import com.example.jpalab.domain.Book;
import com.example.jpalab.repository.AuthorRepository;
import com.example.jpalab.support.QueryCounter;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.hibernate.loader.MultipleBagFetchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 화면에서 1:N 연관관계가 books, awards 두 개 이상 필요한 경우 데모.
 * fetch join/EntityGraph로 컬렉션 두 개를 동시에 채우려 하면 SQL 카티션 곱을
 * Hibernate가 감당 못 해서 예외를 던짐 -> 이런 경우엔 batch size로 우회.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=5")
@Transactional
class MultipleCollectionFetchTest {

    private static final int AUTHOR_COUNT = 10;
    private static final int BOOKS_PER_AUTHOR = 5;
    private static final int AWARDS_PER_AUTHOR = 3;
    private static final int BATCH_SIZE = 5;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private QueryCounter queryCounter;

    @BeforeEach
    void seedAuthorsWithBooksAndAwards() {
        for (int i = 1; i <= AUTHOR_COUNT; i++) {
            Author author = new Author("작가" + i);
            for (int j = 1; j <= BOOKS_PER_AUTHOR; j++) {
                author.addBook(new Book("작가" + i + "의 책" + j));
            }
            for (int k = 1; k <= AWARDS_PER_AUTHOR; k++) {
                author.addAward(new Award("작가" + i + "의 수상" + k));
            }
            authorRepository.save(author);
        }
        em.flush();
        em.clear();
        queryCounter.reset();
    }

    @Test
    void fetchJoiningTwoCollectionsAtOnceThrows() {
        assertThatThrownBy(() -> authorRepository.findAllFetchJoinBooksAndAwards())
            .hasRootCauseInstanceOf(MultipleBagFetchException.class);
    }

    @Test
    void batchFetchSizeHandlesTwoCollectionsWithoutException() {
        List<Author> authors = authorRepository.findAll();
        authors.forEach(author -> {
            author.getBooks().size();
            author.getAwards().size();
        });

        long total = queryCounter.statementCount();
        int expectedBatches = (int) Math.ceil(AUTHOR_COUNT / (double) BATCH_SIZE);
        // findAll 1번 + books 배치 조회 + awards 배치 조회
        int expected = 1 + expectedBatches + expectedBatches;
        System.out.printf(
            "[다중 컬렉션 + batch_fetch_size=%d] 총 쿼리=%d (기대값=%d)%n",
            BATCH_SIZE, total, expected);

        assertThat(total).isEqualTo(expected);
    }
}
