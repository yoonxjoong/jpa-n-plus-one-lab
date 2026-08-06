package com.example.jpalab;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.jpalab.domain.Author;
import com.example.jpalab.domain.Book;
import com.example.jpalab.repository.AuthorRepository;
import com.example.jpalab.support.QueryCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 페이징(Pageable)이 걸린 목록 조회에서 batch size와 컬렉션 fetch join을 비교.
 * 겉으로 리턴되는 페이지 크기는 둘 다 맞아 보이지만, 실제로 DB에서 메모리로
 * 로딩되는 엔티티 개수(entityLoadCount)가 다르다는 걸 보여줌.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=5")
@Transactional
class PagingStrategiesTest {

    private static final int AUTHOR_COUNT = 10;
    private static final int BOOKS_PER_AUTHOR = 5;
    private static final int PAGE_SIZE = 5;

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
    void batchFetchSizeLoadsOnlyCurrentPage() {
        Page<Author> page = authorRepository.findAll(PageRequest.of(0, PAGE_SIZE));
        page.getContent().forEach(author -> author.getBooks().size());

        long loadedAuthors = queryCounter.entityLoadCount();
        System.out.printf(
            "[batch size + paging] 페이지 크기=%d, 실제 로딩된 author 수=%d, 전체=%d%n",
            page.getContent().size(), loadedAuthors, page.getTotalElements());

        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(page.getTotalElements()).isEqualTo(AUTHOR_COUNT);
        // 요청한 페이지 크기만큼만 메모리에 로딩됨 (5명), DB의 나머지 5명은 안 건드림
        assertThat(loadedAuthors).isEqualTo(PAGE_SIZE);
    }

    @Test
    void fetchJoinPagingLoadsEntireTableIntoMemory() {
        Page<Author> page = authorRepository.findAllFetchJoinPaged(PageRequest.of(0, PAGE_SIZE));

        long loadedAuthors = queryCounter.entityLoadCount();
        System.out.printf(
            "[fetch join + paging] 페이지 크기=%d(요청 %d), 실제 로딩된 author 수=%d, 전체=%d%n",
            page.getContent().size(), PAGE_SIZE, loadedAuthors, page.getTotalElements());

        // 리턴되는 페이지 크기 자체는 맞게 보임 -> 겉보기엔 정상 동작하는 것처럼 보인다는 게 함정
        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        // 하지만 실제로는 author 테이블 전체(10명)를 메모리로 끌어온 다음 자바에서 5개로 자른 것
        assertThat(loadedAuthors).isEqualTo(AUTHOR_COUNT);
    }
}
