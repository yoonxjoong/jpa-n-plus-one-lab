package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.jpalab.domain.Author;
import com.example.jpalab.domain.Book;
import com.example.jpalab.dto.AuthorBookCountDto;
import com.example.jpalab.repository.AuthorRepository;
import com.example.jpalab.support.QueryCounter;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * default_batch_fetch_size를 건드리지 않는 순정 설정에서 findAll() 기본 lazy loading,
 * fetch join, EntityGraph, DTO projection 4가지를 같은 데이터로 비교.
 * batch size 비교는 별도 프로퍼티가 필요해서 {@link BatchFetchSizeTest}에서 따로 함.
 *
 * 로컬에서 `docker compose up -d`로 미리 띄운 postgres(application.yml의
 * spring.datasource 설정)에 붙는다는 전제. 각 테스트는 트랜잭션으로 감싸서
 * 끝나면 자동 롤백되므로, 여러 번 실행해도 데이터가 안 쌓임.
 */
@SpringBootTest
@Transactional
class NPlusOneStrategiesTest {

    private static final int AUTHOR_COUNT = 10;
    private static final int BOOKS_PER_AUTHOR = 5;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private EntityManager em;

    @Autowired
    private QueryCounter queryCounter;

    @BeforeEach
    void seedAuthorsWithBooks() {
        for (int i = 1; i <= AUTHOR_COUNT; i++) {
            Author author = new Author("작가" + i);
            for (int j = 1; j <= BOOKS_PER_AUTHOR; j++) {
                author.addBook(new Book("작가" + i + "의 책" + j));
            }
            authorRepository.save(author);
        }
        em.flush();
        em.clear();
        queryCounter.reset();
    }

    @Test
    void defaultLazyLoadingTriggersNPlusOne() {
        List<Author> authors = authorRepository.findAll();
        long afterFindAll = queryCounter.statementCount();

        authors.forEach(author -> author.getBooks().size());
        long afterAccessingBooks = queryCounter.statementCount();

        System.out.printf(
            "[기본 lazy] findAll 직후 쿼리=%d, books 순회 후 누적 쿼리=%d (author 수=%d)%n",
            afterFindAll, afterAccessingBooks, AUTHOR_COUNT);

        assertThat(afterFindAll).isEqualTo(1);
        assertThat(afterAccessingBooks).isEqualTo(AUTHOR_COUNT + 1);
    }

    @Test
    void fetchJoinRunsSingleQuery() {
        List<Author> authors = authorRepository.findAllFetchJoin();
        authors.forEach(author -> author.getBooks().size());

        long total = queryCounter.statementCount();
        System.out.printf("[fetch join] 총 쿼리=%d%n", total);

        assertThat(total).isEqualTo(1);
        assertThat(authors).hasSize(AUTHOR_COUNT);
    }

    @Test
    void entityGraphRunsSingleQuery() {
        List<Author> authors = authorRepository.findAllWithEntityGraph();
        authors.forEach(author -> author.getBooks().size());

        long total = queryCounter.statementCount();
        System.out.printf("[EntityGraph] 총 쿼리=%d%n", total);

        assertThat(total).isEqualTo(1);
        assertThat(authors).hasSize(AUTHOR_COUNT);
    }

    @Test
    void dtoProjectionRunsSingleQueryWithoutLoadingEntities() {
        List<AuthorBookCountDto> result = authorRepository.findAuthorBookCounts();

        long total = queryCounter.statementCount();
        System.out.printf("[DTO projection] 총 쿼리=%d%n", total);

        assertThat(total).isEqualTo(1);
        assertThat(result).hasSize(AUTHOR_COUNT);
        assertThat(result.get(0).getBookCount()).isEqualTo((long) BOOKS_PER_AUTHOR);
    }
}
