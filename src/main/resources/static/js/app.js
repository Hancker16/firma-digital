function uploadPdf() {
    const input = document.getElementById("pdfFile");
    const file = input.files[0];

    if (!file) {
        alert("Seleccione un archivo PDF");
        return;
    }

    // Validar extensión
    if (file.type !== "application/pdf") {
        alert("Solo se permiten archivos PDF");
        return;
    }

    // Validar tamaño (5 MB)
    const maxSize = 5 * 1024 * 1024;
    if (file.size > maxSize) {
        alert("El archivo supera los 5 MB");
        return;
    }

    const formData = new FormData();
    formData.append("file", file);

    fetch("/pdf/upload", {
        method: "POST",
        body: formData
    })
    .then(res => {
        if (!res.ok) throw new Error("Error al subir el PDF");
        return res.text();
    })
    .then(url => {
        // Cerrar modal
        const modal = bootstrap.Modal.getInstance(
            document.getElementById("uploadModal")
        );
        modal.hide();

        // Mostrar PDF
        document.getElementById("pdfViewer").src = url;
    })
    .catch(err => {
        alert(err.message);
    });
}
