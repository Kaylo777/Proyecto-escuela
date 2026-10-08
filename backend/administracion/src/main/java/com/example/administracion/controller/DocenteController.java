package com.example.administracion.controller;

import com.example.administracion.dto.DocenteRequest;
import com.example.administracion.dto.DocenteResponse;
import com.example.administracion.rabbit.DocentePublisher;
import com.example.administracion.repository.DocenteRepository;
import com.example.administracion.service.DocenteMapper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * CRUD de docentes.
 *
 * <p>No tiene seguridad ni reglas de negocio, por diseno: la autorizacion la
 * aplica el gateway antes de que la peticion llegue aca, y la conversion entre
 * entidad y DTO la hace DocenteMapper.</p>
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
    public List<DocenteResponse> listar() {
        return DocenteMapper.toResponseList(
                repository.findAll(Sort.by(Sort.Direction.ASC, "id")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocenteResponse> obtener(@PathVariable Long id) {
        return repository.findById(id)
                .map(DocenteMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocenteResponse crear(@Valid @RequestBody DocenteRequest request) {
        var guardado = repository.save(DocenteMapper.toEntity(request));
        publisher.publicarDocenteCreado(guardado);
        return DocenteMapper.toResponse(guardado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocenteResponse> actualizar(@PathVariable Long id,
                                                       @Valid @RequestBody DocenteRequest request) {
        return repository.findById(id)
                .map(existente -> {
                    DocenteMapper.actualizar(existente, request);
                    return DocenteMapper.toResponse(repository.save(existente));
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}