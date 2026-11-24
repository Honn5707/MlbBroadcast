package com.mlbbroadcast.bet;

import org.springframework.dao.PessimisticLockingFailureException;

import java.sql.SQLException;

//비관적 락 획득 실패(대기 시간 초과/교착) 판별. 예외 변환 위치에 따라 Spring/JPA/Hibernate/JDBC 예외 중 하나로 올라옴
public final class LockFailures {
    private static final int MYSQL_LOCK_WAIT_TIMEOUT = 1205;
    private static final int MYSQL_DEADLOCK = 1213;

    private LockFailures() {}

    public static boolean isLockFailure(Throwable e){
        for(Throwable t = e; t != null; t = t.getCause()){
            if(t instanceof PessimisticLockingFailureException) return true;
            if(t instanceof jakarta.persistence.PessimisticLockException || t instanceof jakarta.persistence.LockTimeoutException) return true;
            if(t instanceof org.hibernate.PessimisticLockException) return true;
            if(t instanceof SQLException sql && (sql.getErrorCode() == MYSQL_LOCK_WAIT_TIMEOUT || sql.getErrorCode() == MYSQL_DEADLOCK)) return true;
            if(t.getCause() == t) break;
        }
        return false;
    }
}
