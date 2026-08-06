package com.example.jpalab.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.stereotype.Component;

/**
 * prepareStatementCount = 실제로 DB에 날아간 JDBC PreparedStatement 총 개수.
 * lazy loading으로 트리거되는 개별 SELECT까지 전부 잡힘 (queryExecutionCount는
 * 명시적 HQL/JPQL 실행만 세고 지연로딩 SELECT는 안 잡히므로 이 지표를 씀).
 */
@Component
public class QueryCounter {

    private final Statistics statistics;

    public QueryCounter(EntityManagerFactory emf) {
        this.statistics = emf.unwrap(SessionFactory.class).getStatistics();
        this.statistics.setStatisticsEnabled(true);
    }

    public void reset() {
        statistics.clear();
    }

    public long statementCount() {
        return statistics.getPrepareStatementCount();
    }

    /**
     * 실제로 메모리에 로딩된 엔티티 총 개수. 쿼리 수는 적어도(1~2번) 컬렉션
     * fetch join + 페이징을 잘못 조합하면 이 값이 요청한 페이지 크기보다
     * 훨씬 커질 수 있음 -> "쿼리 수만으로는 안 보이는 문제"를 잡는 지표.
     */
    public long entityLoadCount() {
        return statistics.getEntityLoadCount();
    }
}
