package tn.uib.bnpl.gestion_utilisateur.services;

import org.springframework.stereotype.Service;
import tn.uib.bnpl.gestion_utilisateur.classes.AccountStatus;
import tn.uib.bnpl.gestion_utilisateur.classes.Banque;
import tn.uib.bnpl.gestion_utilisateur.classes.Role;
import tn.uib.bnpl.gestion_utilisateur.dto.AnalysteRoutageDto;
import tn.uib.bnpl.gestion_utilisateur.repository.BanqueRepository;
import tn.uib.bnpl.gestion_utilisateur.repository.UserRepository;

import java.util.List;

@Service
public class BanqueRoutageServiceImpl implements BanqueRoutageService {

    private final BanqueRepository banqueRepository;
    private final UserRepository userRepository;

    public BanqueRoutageServiceImpl(BanqueRepository banqueRepository, UserRepository userRepository) {
        this.banqueRepository = banqueRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<AnalysteRoutageDto> listerAnalystesActifsParCodeBanque(String codeBanque) {
        if (codeBanque == null || codeBanque.isBlank()) {
            return List.of();
        }
        return banqueRepository.findByCodeBanqueIgnoreCase(codeBanque.trim())
                .map(this::listerAnalystesActifs)
                .orElse(List.of());
    }

    private List<AnalysteRoutageDto> listerAnalystesActifs(Banque banque) {
        return userRepository.findByRoleAndBanque_IdAndStatus(
                        Role.ANALYSTE_BANCAIRE, banque.getId(), AccountStatus.ACTIVE)
                .stream()
                .map(u -> new AnalysteRoutageDto(
                        u.getId(),
                        u.getEmail(),
                        u.getNom(),
                        u.getPrenom(),
                        banque.getId(),
                        banque.getCodeBanque(),
                        banque.getNomBanque()))
                .toList();
    }
}
