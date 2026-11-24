package com.mlbbroadcast.bet;

import com.mlbbroadcast.bet.dto.BetResponse;
import com.mlbbroadcast.bet.dto.PlaceBetRequest;
import com.mlbbroadcast.coin.CoinTransactionService;
import com.mlbbroadcast.common.Side;
import com.mlbbroadcast.configuration.BusinessException;
import com.mlbbroadcast.configuration.ErrorCode;
import com.mlbbroadcast.match.entities.Matches;
import com.mlbbroadcast.match.enums.MatchStatus;
import com.mlbbroadcast.match.repositories.MatchesRepository;
import com.mlbbroadcast.member.Members;
import com.mlbbroadcast.member.MembersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

//베팅 생성 (docs/design/Bet/BetSystemDesign.md 4장)
//트랜잭션 밖: 금액 검증, 경기 상태 확인, 중복 확인 / 트랜잭션 안: 회원 락 조회 -> 잔액 확인 -> Bet 생성 -> 코인 차감 + BET_PLACED 기록
@Slf4j
@Service
@RequiredArgsConstructor
public class BetService {
    private final BetRepository betRepository;
    private final MatchesRepository matchesRepository;
    private final MembersRepository membersRepository;
    private final CoinTransactionService coinTransactionService;
    private final BetProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public BetResponse placeBet(Long memberId, PlaceBetRequest request){
        int amount = request.amount();
        if(amount < properties.getMinAmount() || amount > properties.getMaxAmount())
            throw new BusinessException(ErrorCode.BET_INVALID_AMOUNT);
        if(request.betSide() != Side.HOME && request.betSide() != Side.VISIT)
            throw new BusinessException(ErrorCode.BET_INVALID_SIDE);

        Matches match = matchesRepository.findById(request.matchId()).orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
        LocalDateTime now = now();
        //SCHEDULED라도 시작 시각이 지났으면 마감 (LIVE 감지까지 최대 1분 지연이 있으므로 시각도 함께 확인)
        if(match.getMatchStatus() != MatchStatus.SCHEDULED || !now.isBefore(match.getScheduledStartedTime()))
            throw new BusinessException(ErrorCode.BET_CLOSED);
        if(betRepository.existsByMemberIdAndMatchId(memberId, match.getId()))
            throw new BusinessException(ErrorCode.BET_ALREADY_PLACED);

        try {
            return transactionTemplate.execute(status -> {
                Members member = membersRepository.findByIdForUpdate(memberId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
                if(member.getCoinRemain() < amount) throw new BusinessException(ErrorCode.LACK_COIN);

                //UNIQUE(member_id, match_id) 위반을 여기서 바로 드러내기 위해 flush
                Bet bet = betRepository.saveAndFlush(Bet.builder()
                        .matchId(match.getId()).memberId(memberId).amount(amount).betSide(request.betSide()).createdAt(now).build());
                coinTransactionService.spendForBet(member, bet.getId(), amount);
                return BetResponse.of(bet, member.getCoinRemain());
            });
        } catch (DataIntegrityViolationException e) {
            //중복 확인 후 동시에 들어온 같은 회원의 같은 경기 베팅
            throw new BusinessException(ErrorCode.BET_ALREADY_PLACED);
        } catch (RuntimeException e) {
            if(LockFailures.isLockFailure(e)){
                log.warn("베팅 생성 중 회원 락 대기 초과: memberId={}, matchId={}", memberId, match.getId());
                throw new BusinessException(ErrorCode.BET_LOCK_TIMEOUT);
            }
            throw e;
        }
    }

    private LocalDateTime now(){
        return LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
    }
}
