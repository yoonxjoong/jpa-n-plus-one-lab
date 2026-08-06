package com.example.jpalab.repository;

import com.example.jpalab.domain.Author;
import com.example.jpalab.dto.AuthorBookCountDto;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // 기본 findAll(): books는 LAZY라 여기선 안 채워짐 -> 나중에 접근하는 순간 N+1 발생
    // 기본 findAll(Pageable)도 JpaRepository가 자동 제공 -> 페이징+batch size 케이스에서 그대로 사용

    @Query("select distinct a from Author a join fetch a.books")
    List<Author> findAllFetchJoin();

    @EntityGraph(attributePaths = "books")
    @Query("select a from Author a")
    List<Author> findAllWithEntityGraph();

    @Query("""
        select new com.example.jpalab.dto.AuthorBookCountDto(a.id, a.name, count(b))
        from Author a left join a.books b
        group by a.id, a.name
        """)
    List<AuthorBookCountDto> findAuthorBookCounts();

    // 컬렉션 두 개(books, awards)를 동시에 join fetch -> Hibernate가 카티션 곱을 감당 못 해서
    // MultipleBagFetchException을 던짐. "왜 fetch join이 만능이 아닌지"를 보여주는 반례.
    @Query("select distinct a from Author a join fetch a.books join fetch a.awards")
    List<Author> findAllFetchJoinBooksAndAwards();

    // 컬렉션을 join fetch하면서 동시에 페이징하면, SQL의 LIMIT/OFFSET을 못 쓰고
    // Hibernate가 전체 행을 다 끌어온 뒤 메모리에서 잘라냄(HHH000104 경고).
    // page size를 작게 넣어도 실제로는 author 테이블 전체를 로딩하게 되는 안티패턴 데모.
    @Query(value = "select distinct a from Author a left join fetch a.books",
           countQuery = "select count(a) from Author a")
    Page<Author> findAllFetchJoinPaged(Pageable pageable);
}
