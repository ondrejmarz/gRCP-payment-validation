package cz.cvut.fit.marzondr.grpcservicecomposition;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.stub.StreamObserver;
import model.Model.*;
import model.CardOuterClass.*;
import model.PaymentServiceGrpc;
import model.CardsServiceGrpc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PaymentService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private static List<Payment> paymentList = new ArrayList<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        Server server = ServerBuilder
                .forPort(8000)
                .addService(new PaymentService())
                .addService(ProtoReflectionService.newInstance())
                .build();

        server.start();
        server.awaitTermination();
    }

    @Override
    public void listPayments(Empty request, StreamObserver<PaymentList> responseObserver) {
        PaymentList paymentListResponse = PaymentList.newBuilder().addAllPayment(paymentList).build();
        responseObserver.onNext(paymentListResponse);
        responseObserver.onCompleted();
    }

    // ověření platby pomocí Card Validation gRPC Service
    private boolean validateCard(String cardNumber, String cardOwner) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("ni-am.fit.cvut.cz", 9090)
                .usePlaintext()
                .build();

        try {
            CardsServiceGrpc.CardsServiceBlockingStub stub = CardsServiceGrpc.newBlockingStub(channel);

            // připravení karty (Card) ke schválení
            Card card = Card.newBuilder()
                    .setCardNumber(cardNumber)
                    .setCardOwner(cardOwner)
                    .build();

            // volání cizí gRCP metody
            return stub.validateCard(card).getValue();

        } catch (Exception e) {
            System.err.println("Error calling validateCard: " + e.getMessage());
            return false;
        } finally {
            channel.shutdown();
        }
    }

    @Override
    public void addPayment(Payment paymentRequest, StreamObserver<PaymentResponse> responseObserver) {

        // validace platby pomocí volání validační service
        boolean isValidCard = validateCard(paymentRequest.getCreditCardNumber(), paymentRequest.getCreditCardOwner());

        if (isValidCard) {
            // platba je ověřena a provede se
            Payment payment = Payment.newBuilder()
                    .setCreditCardNumber(paymentRequest.getCreditCardNumber())
                    .setCreditCardOwner(paymentRequest.getCreditCardOwner())
                    .setOrderIdentifier(paymentRequest.getOrderIdentifier())
                    .build();

            paymentList.add(payment);

            // odpověď o úspěchu platby
            PaymentResponse response = PaymentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Payment added successfully")
                    .build();

            responseObserver.onNext(response);
        } else {
            // odpověď o zamítnuté platbě
            PaymentResponse response = PaymentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Invalid credit card")
                    .build();

            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }
}
