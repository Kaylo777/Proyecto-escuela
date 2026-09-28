package com.example.administracion.controller;

import com.example.administracion.model.Docente;
import com.example.administracion.rabbit.DocentePublisher;
import com.example.administracion.repository.DocenteRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD de docentes. El token JWT llega validado desde el gateway y tambien se
 * valida aqui (resource server).
 *
 * <p>Los @PreAuthorize son el segundo nivel de defensa: aunque las reglas de
 * URL de SecurityConfig se relajasen por error, el metodo seguiria protegido.</p>
 */
@RestController
@RequestMapping("/api/administracion")
public class DocenteController {

    private final DocenteRepository repository;
    private final DocentePublisher publisher;

    public DocenteController(DocenteRepository repository, DocentePublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @GetMapping
    public List<Docente> listar() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Docente> obtener(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Docente crear(@RequestBody Docente docente) {
        Docente guardado = repository.save(docente);
        publisher.publicarDocenteCreado(guardado);
        return guardado;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Docente> actualizar(@PathVariable Long id, @RequestBody Docente datos) {
        return repository.findById(id).map(existente -> {
            existente.setNombre(datos.getNombre());
            existente.setApellido(datos.getApellido());
            existente.setEmail(datos.getEmail());
            existente.setEspecialidad(datos.getEspecialidad());
            return ResponseEntity.ok(repository.save(existente));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}