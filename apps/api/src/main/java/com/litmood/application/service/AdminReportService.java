package com.litmood.application.service;

import com.litmood.domain.exception.LitmoodException;
import com.litmood.domain.model.Collection;
import com.litmood.domain.model.Record;
import com.litmood.domain.model.Report;
import com.litmood.domain.model.Report.ReportStatus;
import com.litmood.domain.model.Report.ReportTarget;
import com.litmood.domain.model.User;
import com.litmood.domain.repository.CollectionRepository;
import com.litmood.domain.repository.RecordRepository;
import com.litmood.domain.repository.ReportRepository;
import com.litmood.domain.repository.UserRepository;
import com.litmood.interfaces.dto.AdminDtos.AdminReportPage;
import com.litmood.interfaces.dto.AdminDtos.AdminReportResponse;
import com.litmood.interfaces.dto.AdminDtos.AdminReportTarget;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 신고 처리 큐 (#28).
 *
 * <p>목록은 <b>신고 건별</b>로 평면이다. 같은 대상의 신고를 하나로 묶으면 서로 다른
 * 사유가 접혀 판단 근거가 사라지고, 상태 전이는 어차피 건별이라 묶음 단위 버튼과
 * 어긋난다. 대신 각 건에 {@code sameTargetCount} 를 실어, "여러 사람이 같은 것을
 * 신고했다" 는 신호는 목록에서 바로 보이게 했다.
 *
 * <p>처리 결과에 따른 <b>조치</b>(기록 숨김·계정 정지)는 여기 없다. 정지는 상태·해제
 * 경로·이의 절차가 따라오는 별개의 기능이고, 지금 필요한 것은 쌓인 신고를 읽고
 * 큐를 비우는 길이다. 조치는 이 화면에서 대상으로 이동해 손으로 한다.
 */
@Service
@Transactional(readOnly = true)
public class AdminReportService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReportRepository reportRepository;
    private final RecordRepository recordRepository;
    private final CollectionRepository collectionRepository;
    private final UserRepository userRepository;

    public AdminReportService(
            ReportRepository reportRepository,
            RecordRepository recordRepository,
            CollectionRepository collectionRepository,
            UserRepository userRepository) {
        this.reportRepository = reportRepository;
        this.recordRepository = recordRepository;
        this.collectionRepository = collectionRepository;
        this.userRepository = userRepository;
    }

    public AdminReportPage queue(ReportStatus status, String cursorToken, Integer limit) {
        int size = limit == null ? DEFAULT_PAGE_SIZE : Math.clamp(limit, 1, MAX_PAGE_SIZE);
        Optional<Cursor> cursor = Cursor.decode(cursorToken);

        List<Report> found = reportRepository.findQueue(
                status,
                cursor.map(Cursor::createdAt).orElse(null),
                cursor.map(Cursor::id).orElse(null),
                // 다음 페이지 존재 여부를 알기 위해 1건 더 가져온다
                size + 1);

        boolean hasMore = found.size() > size;
        List<Report> items = hasMore ? found.subList(0, size) : found;

        String nextCursor = null;
        if (hasMore) {
            Report last = items.get(items.size() - 1);
            nextCursor = new Cursor(last.getCreatedAt(), last.getId()).encode();
        }

        return new AdminReportPage(
                describe(items),
                nextCursor,
                reportRepository.countByStatus(status),
                reportRepository.countByStatus(ReportStatus.PENDING));
    }

    @Transactional
    public AdminReportResponse resolve(Long reportId, ReportStatus decision) {
        Report report = reportRepository
                .findById(reportId)
                .orElseThrow(() -> LitmoodException.notFound("신고"));
        // 규칙(한 번만 처리한다)은 엔티티가 지킨다 — 여기서 상태를 검사하지 않는다
        report.resolve(decision);
        return describe(List.of(reportRepository.save(report))).get(0);
    }

    /**
     * 신고 목록에 대상·신고자·누적 건수를 채운다.
     *
     * <p>항목마다 조회하면 한 페이지에 수십 번이 나간다. 대상 종류별로 한 번씩,
     * 사용자는 (신고자 + 사용자 대상 + 컬렉션 소유자)를 합쳐 한 번에 가져온다.
     */
    private List<AdminReportResponse> describe(List<Report> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }

        Map<ReportTarget, List<Long>> targetIds = new EnumMap<>(ReportTarget.class);
        for (ReportTarget type : ReportTarget.values()) {
            targetIds.put(
                    type,
                    reports.stream()
                            .filter(report -> report.getTargetType() == type)
                            .map(Report::getTargetId)
                            .distinct()
                            .toList());
        }

        Map<Long, Record> records = byId(
                recordRepository.findAllByIds(targetIds.get(ReportTarget.RECORD)), Record::getId);
        Map<Long, Collection> collections = byId(
                collectionRepository.findAllByIds(targetIds.get(ReportTarget.COLLECTION)), Collection::getId);

        Set<Long> userIds = new HashSet<>(targetIds.get(ReportTarget.USER));
        reports.forEach(report -> userIds.add(report.getReporterId()));
        collections.values().forEach(collection -> userIds.add(collection.getUserId()));
        Map<Long, User> users = byId(userRepository.findAllByIds(List.copyOf(userIds)), User::getId);

        Map<ReportTarget, Map<Long, Long>> counts = new EnumMap<>(ReportTarget.class);
        for (ReportTarget type : ReportTarget.values()) {
            counts.put(type, reportRepository.countByTargets(type, targetIds.get(type)));
        }

        return reports.stream()
                .map(report -> {
                    User reporter = users.get(report.getReporterId());
                    return new AdminReportResponse(
                            report.getId(),
                            report.getReason(),
                            report.getDetail(),
                            report.getStatus(),
                            target(report, records, collections, users),
                            reporter == null ? null : reporter.getHandle(),
                            reporter == null ? null : reporter.getNickname(),
                            counts.get(report.getTargetType()).getOrDefault(report.getTargetId(), 1L),
                            report.getCreatedAt(),
                            report.getResolvedAt());
                })
                .toList();
    }

    private AdminReportTarget target(
            Report report,
            Map<Long, Record> records,
            Map<Long, Collection> collections,
            Map<Long, User> users) {

        Long id = report.getTargetId();
        return switch (report.getTargetType()) {
            case RECORD -> {
                Record record = records.get(id);
                // 대상 행 자체가 사라졌으면(하드 삭제) 남은 정보가 id 뿐이다
                if (record == null) {
                    yield missing(ReportTarget.RECORD, id);
                }
                User author = record.getAuthor();
                yield new AdminReportTarget(
                        ReportTarget.RECORD,
                        id,
                        record.getContent() == null ? null : record.getContent().getTitle(),
                        author == null ? null : author.getHandle(),
                        null,
                        record.getDeletedAt() != null);
            }
            case COLLECTION -> {
                Collection collection = collections.get(id);
                if (collection == null) {
                    yield missing(ReportTarget.COLLECTION, id);
                }
                User owner = users.get(collection.getUserId());
                yield new AdminReportTarget(
                        ReportTarget.COLLECTION,
                        id,
                        collection.getTitle(),
                        owner == null ? null : owner.getHandle(),
                        collection.getSlug(),
                        collection.getDeletedAt() != null);
            }
            case USER -> {
                User user = users.get(id);
                if (user == null) {
                    yield missing(ReportTarget.USER, id);
                }
                yield new AdminReportTarget(
                        ReportTarget.USER,
                        id,
                        user.getNickname(),
                        user.getHandle(),
                        null,
                        user.getDeletedAt() != null);
            }
        };
    }

    private AdminReportTarget missing(ReportTarget type, Long id) {
        return new AdminReportTarget(type, id, null, null, null, true);
    }

    private <T> Map<Long, T> byId(List<T> entities, Function<T, Long> id) {
        return entities.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
