import SwiftUI
import AVFoundation

struct PulseCameraView: View {
    @StateObject private var viewModel = PulseViewModel()
    @State private var cameraAuthorized = false
    @AppStorage("has_seen_intro") private var hasSeenIntro = false
    @State private var showInfo = false

    var body: some View {
        Group {
            if hasSeenIntro {
                ZStack {
                    if cameraAuthorized {
                        CameraPreview(position: .back)
                    } else {
                        Color.black
                    }
                    ClusterDots(clusters: viewModel.clusters)
                    VStack {
                        HStack {
                            Spacer()
                            InfoButton {
                                showInfo = true
                            }
                        }
                        Spacer()
                    }
                    .padding(16)
                }
                .onAppear {
                    requestCameraAccess()
                }
                .sheet(isPresented: $showInfo) {
                    InfoSheet(clusters: viewModel.clusters)
                }
            } else {
                IntroView {
                    hasSeenIntro = true
                    requestCameraAccess()
                }
            }
        }
    }

    private func requestCameraAccess() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            cameraAuthorized = true
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async {
                    cameraAuthorized = granted
                }
            }
        default:
            cameraAuthorized = false
        }
    }
}

struct ClusterDots: View {
    let clusters: [ClusterViewData]

    var body: some View {
        GeometryReader { proxy in
            ForEach(clusters) { cluster in
                PulsingDot()
                    .position(dotPosition(for: cluster.id, in: proxy.size))
            }
        }
        .allowsHitTesting(false)
    }

    private func dotPosition(for clusterId: String, in size: CGSize) -> CGPoint {
        let hash = clusterId.unicodeScalars.reduce(0) { $0 * 31 + Int($1.value) }
        let xSeed = CGFloat((hash & 0xFFFF) % 1000) / 1000.0
        let ySeed = CGFloat(((hash >> 16) & 0xFFFF) % 1000) / 1000.0
        let padding: CGFloat = 40
        let x = padding + (size.width - padding * 2) * xSeed
        let y = padding + (size.height - padding * 2) * ySeed
        return CGPoint(x: x, y: y)
    }
}

struct PulsingDot: View {
    @State private var pulse = false

    var body: some View {
        Circle()
            .fill(Color.red.opacity(0.8))
            .frame(width: pulse ? 20 : 10, height: pulse ? 20 : 10)
            .overlay(
                Circle()
                    .stroke(Color.red.opacity(0.6), lineWidth: 2)
                    .frame(width: pulse ? 36 : 20, height: pulse ? 36 : 20)
                    .opacity(pulse ? 0.2 : 0.8)
            )
            .onAppear {
                withAnimation(
                    Animation.easeInOut(duration: 1.2)
                        .repeatForever(autoreverses: true)
                ) {
                    pulse.toggle()
                }
            }
    }
}

struct IntroView: View {
    let onStart: () -> Void
    @State private var pulse = false

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color.black, Color(red: 0.08, green: 0.08, blue: 0.1)],
                startPoint: .top,
                endPoint: .bottom
            )
            VStack(spacing: 18) {
                ZStack {
                    Circle()
                        .fill(Color.red.opacity(0.7))
                        .frame(width: pulse ? 70 : 50, height: pulse ? 70 : 50)
                    Circle()
                        .stroke(Color.red.opacity(0.35), lineWidth: 3)
                        .frame(width: pulse ? 110 : 80, height: pulse ? 110 : 80)
                }
                .onAppear {
                    withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true)) {
                        pulse.toggle()
                    }
                }
                Text("Passive signal sensing. No identities.")
                    .font(.body)
                    .foregroundColor(.white.opacity(0.85))
                Button(action: onStart) {
                    Text("Start scanning")
                        .font(.headline)
                        .foregroundColor(.black)
                        .padding(.vertical, 12)
                        .padding(.horizontal, 28)
                        .background(Color.white)
                        .clipShape(Capsule())
                }
            }
        }
        .ignoresSafeArea()
    }
}

struct InfoButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "info")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 36, height: 36)
                .background(BlurView(style: .systemUltraThinMaterialDark))
                .clipShape(Circle())
                .overlay(
                    Circle().stroke(Color.white.opacity(0.4), lineWidth: 1)
                )
        }
    }
}

struct InfoSheet: View {
    let clusters: [ClusterViewData]

    var body: some View {
        let totalDevices = clusters.reduce(0) { $0 + $1.deviceCount }
        let confidence: String = {
            if clusters.contains(where: { $0.confidence.contains("High") }) { return "High" }
            if clusters.contains(where: { $0.confidence.contains("Medium") }) { return "Medium" }
            return "Low"
        }()
        let stationaryCount = clusters.filter { $0.stability == "Stationary" }.count

        VStack(alignment: .leading, spacing: 12) {
            Text("We found \(totalDevices) devices around you.")
                .font(.title3.weight(.semibold))
            HStack(spacing: 12) {
                Text("Confidence: \(confidence)")
                Text("Stationary: \(stationaryCount)")
            }
            .font(.subheadline)
            .foregroundColor(.secondary)
            Text("Passive Bluetooth signals only. No identities stored.")
                .font(.footnote)
                .foregroundColor(.secondary)
            Spacer()
        }
        .padding(20)
        .presentationDetents([.medium])
    }
}

struct BlurView: UIViewRepresentable {
    let style: UIBlurEffect.Style

    func makeUIView(context: Context) -> UIVisualEffectView {
        UIVisualEffectView(effect: UIBlurEffect(style: style))
    }

    func updateUIView(_ uiView: UIVisualEffectView, context: Context) {}
}

struct CameraPreview: UIViewRepresentable {
    let position: AVCaptureDevice.Position

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeUIView(context: Context) -> UIView {
        let view = PreviewView(frame: .zero)
        context.coordinator.controller.attachPreview(to: view)
        context.coordinator.controller.start(position: position)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.controller.updateFrame(for: uiView)
    }

    static func dismantleUIView(_ uiView: UIView, coordinator: Coordinator) {
        coordinator.controller.stop()
    }

    final class Coordinator {
        let controller = CameraSessionController()
    }
}

final class CameraSessionController {
    private let session = AVCaptureSession()
    private let previewLayer = AVCaptureVideoPreviewLayer()
    private let sessionQueue = DispatchQueue(label: "pulse.camera.session")
    private var configured = false

    func attachPreview(to view: UIView) {
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.session = session
        view.layer.addSublayer(previewLayer)
        previewLayer.frame = view.bounds
        previewLayer.needsDisplayOnBoundsChange = true
    }

    func updateFrame(for view: UIView) {
        previewLayer.frame = view.bounds
    }

    func start(position: AVCaptureDevice.Position) {
        sessionQueue.async {
            if !self.configured {
                self.configureSession(position: position)
            }
            if !self.session.isRunning {
                self.session.startRunning()
            }
        }
    }

    func stop() {
        sessionQueue.async {
            if self.session.isRunning {
                self.session.stopRunning()
            }
        }
    }

    private func configureSession(position: AVCaptureDevice.Position) {
        session.beginConfiguration()
        session.sessionPreset = .high
        session.inputs.forEach { session.removeInput($0) }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            session.commitConfiguration()
            return
        }
        session.addInput(input)
        if let connection = previewLayer.connection, connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }
        session.commitConfiguration()
        configured = true
    }
}

final class PreviewView: UIView {
    override func layoutSubviews() {
        super.layoutSubviews()
        layer.sublayers?.forEach { layer in
            layer.frame = bounds
        }
    }
}
