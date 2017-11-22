//### Importing key libraries:
import org.apache.spark.h2o._
import org.apache.spark.SparkFiles
import org.apache.spark.examples.h2o._
import org.apache.spark.sql.{DataFrame, SQLContext}
import water.Key
import water.support.SparkContextSupport.addFiles
import water.support.H2OFrameSupport._
import java.io.File


//### Creating H2O Context:
val h2oContext = H2OContext.getOrCreate(sc)
import h2oContext._
import h2oContext.implicits._


//### Importing titanic data from a file stored on local file system:
val housePriceData = new H2OFrame(new File("/Users/avkashchauhan/learn/customers/house_price_train.csv"))
val housePriceTestData = new H2OFrame(new File("/Users/avkashchauhan/learn/customers/house_price_test.csv"))


//### Importing Deep Learning specific libraries:
import _root_.hex.deeplearning.DeepLearning
import _root_.hex.deeplearning.DeepLearningModel
import _root_.hex.deeplearning.DeepLearningModel.DeepLearningParameters


//### Creating Deep Learning Regression Model using titanic data ingested previously:
val dlParams = new DeepLearningParameters()
dlParams._train = housePriceData


//### Setting fold assignment to Muldulo
import _root_.hex.Model.Parameters.FoldAssignmentScheme
dlParams._fold_assignment = FoldAssignmentScheme.Modulo

//### Setting remaining Deep Learning Parameters:
dlParams._response_column = 'medv
dlParams._nfolds = 5
dlParams._seed= 11111
dlParams._epochs = 10
dlParams._train_samples_per_iteration = 20
dlParams._score_training_samples = 50
dlParams._activation = DeepLearningParameters.Activation.Rectifier
dlParams._variable_importances = true
dlParams._keep_cross_validation_predictions = true
dlParams._adaptive_rate = false

//### Creating Deep Learning Model with supplied key name
val dl = new DeepLearning(dlParams, Key.make("dlHousePriceRegressionModel.hex"))
val dlHousePriceModel = dl.trainModel.get()

//### Getting Model Details built in previous step:
dlHousePriceModel

//### Importing GBM specific libraries:
import _root_.hex.tree.gbm.GBM
import _root_.hex.tree.gbm.GBMModel.GBMParameters

//### Setting GBM Parameters:
val gbmParams = new GBMParameters()
gbmParams._train = housePriceData
gbmParams._response_column = 'medv
gbmParams._nfolds = 5
gbmParams._seed = 1111
gbmParams._fold_assignment = FoldAssignmentScheme.Modulo
gbmParams._keep_cross_validation_predictions = true;

//### Creating GBM Model with supplied key name
val gbm = new GBM(gbmParams,Key.make("gbmHousePriceRegressionModel.hex"))
val gbmHousePriceModel = gbm.trainModel().get()

//### Getting Model Details built in previous step:
gbmHousePriceModel


//### Importing Stacked Ensemble specific libraries:
import _root_.hex.Model
import _root_.hex.StackedEnsembleModel
import _root_.hex.ensemble.StackedEnsemble

//### Setting dataset and response column details with stacked ensemble
val stackedEnsembleParameters = new StackedEnsembleModel.StackedEnsembleParameters()
stackedEnsembleParameters._train = housePriceData._key
stackedEnsembleParameters._response_column = 'medv

//### Setting models (both GBM and Deep Learning) as based models for stacked ensemble
type T_MODEL_KEY = Key[Model[_, _ <: Model.Parameters, _ <:Model.Output]]
stackedEnsembleParameters._base_models = Array(gbmHousePriceModel._key.asInstanceOf[T_MODEL_KEY], dlHousePriceModel._key.asInstanceOf[T_MODEL_KEY])

//### Building Stacked Ensemble Model
val stackedEnsembleJob = new StackedEnsemble(stackedEnsembleParameters)
val stackedEnsembleModel = stackedEnsembleJob.trainModel().get();

//### Getting Model summary and training metrics 
stackedEnsembleModel._output._model_summary
stackedEnsembleModel._output._training_metrics

//### Getting Model Metrics
stackedEnsembleModel.rmsle
stackedEnsembleModel.mse
stackedEnsembleModel.loss

//### Getting Model features and classes
stackedEnsembleModel._output.nfeatures
stackedEnsembleModel._output.nclasses

//### Getting Model classes, domains and checking if it is supervised or not
stackedEnsembleModel._output.isSupervised
stackedEnsembleModel._output.classNames
stackedEnsembleModel._output._domains

//### Performing Predictions with the Model built earlier
val predH2OFrame = stackedEnsembleModel.score(housePriceTestData)('predict)
val predFromModel = asRDD[DoubleHolder](predH2OFrame).collect.map(_.result.getOrElse(Double.NaN))
