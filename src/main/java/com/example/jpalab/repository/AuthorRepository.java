package com.example.jpalab.repository;

import com.example.jpalab.domain.Author;
import com.example.jpalab.dto.AuthorBookCountDto;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuthorRepository extends JpaRepository<Author, Long> {

    // 기본 findAll(): books는 LAZY라 여기선 안 채워짐 -> 나중에 접근하는 순간 N+1 발생

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
}
