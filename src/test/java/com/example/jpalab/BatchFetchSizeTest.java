package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.jpalab.domain.Author;
import com.example.jpalab.domain.Book;
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
 * hibernate.default_batch_fetch_size는 엔티티 코드 변경 없이 프로퍼티만으로 N+1을
 * 줄여주는 전략이라, 다른 테스트와 스프링 컨텍스트(프로퍼티)가 달라서 별도 클래스로 분리.
 * author 10명 / batch size 5 -> IN 절로 2번에 나눠 배치 조회될 것으로 기대.
 *
 * 로컬에서 `docker compose up -d`로 미리 띄운 postgres(application.yml의
 * spring.datasource 설정)에 붙는다는 전제. 트랜잭션으로 감싸서 끝나면 롤백.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=5")
@Transactional
class BatchFetchSizeTest {

    private static final int AUTHOR_COUNT = 10;
    private static final int BOOKS_PER_AUTHOR = 5;
    private static final int BATCH_SIZE = 5;

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
    void batchFetchSizeGroupsLazyLoadsIntoFewerQueries() {
        List<Author> authors = authorRepository.findAll();
        authors.forEach(author -> author.getBooks().size());

        long total = queryCounter.statementCount();
        int expectedBatches = (int) Math.ceil(AUTHOR_COUNT / (double) BATCH_SIZE);
        System.out.printf(
            "[batch_fetch_size=%d] 총 쿼리=%d (author 수=%d, 기대 배치 조회 횟수=%d)%n",
            BATCH_SIZE, total, AUTHOR_COUNT, expectedBatches);

        assertThat(total).isEqualTo(1 + expectedBatches);
    }
}
