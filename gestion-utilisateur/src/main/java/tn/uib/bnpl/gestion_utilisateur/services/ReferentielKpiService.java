package tn.uib.bnpl.gestion_utilisateur.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.uib.bnpl.gestion_utilisateur.classes.AccountStatus;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;
import tn.uib.bnpl.gestion_utilisateur.classes.User;
import tn.uib.bnpl.gestion_utilisateur.dto.ReferentielKpiDto;
import tn.uib.bnpl.gestion_utilisateur.repository.BanqueRepository;
import tn.uib.bnpl.gestion_utilisateur.repository.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class ReferentielKpiService {

    private final UserRepository userRepository;
    private final BanqueRepository banqueRepository;

    public ReferentielKpiService(UserRepository userRepository, BanqueRepository banqueRepository) {
        this.userRepository = userRepository;
        this.banqueRepository = banqueRepository;
    }

    public ReferentielKpiDto snapshot() {
        return new ReferentielKpiDto(
                userRepository.countByRole(Role.CLIENT),
                userRepository.countByRole(Role.COMMERCANT),
                banqueRepository.count(),
                userRepository.countByStatusAndRoleNot(AccountStatus.ACTIVE, Role.ADMIN),
                userRepository.countByRoleNot(Role.ADMIN),
                commercantLabels()
        );
    }

    public Map<String, String> commercantLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (User user : userRepository.findByRole(Role.COMMERCANT)) {
            labels.put(String.valueOf(user.getId()), formatCommercantLabel(user));
        }
        return labels;
    }

    private static String formatCommercantLabel(User user) {
        if (user.getNomMagasin() != null && !user.getNomMagasin().isBlank()) {
            return user.getNomMagasin().trim();
        }
        String name = Stream.of(user.getPrenom(), user.getNom())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
        if (!name.isEmpty()) {
            return name;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail().trim();
        }
        return "Commerçant #" + user.getId();
    }
}
